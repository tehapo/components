package com.vaadin.components.grid;

import static com.google.gwt.query.client.GQuery.$;
import static com.google.gwt.query.client.GQuery.console;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.js.JsExport;
import com.google.gwt.core.client.js.JsNamespace;
import com.google.gwt.core.client.js.JsNoExport;
import com.google.gwt.core.client.js.JsType;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.query.client.Function;
import com.google.gwt.query.client.js.JsUtils;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.vaadin.client.widget.grid.EditorHandler;
import com.vaadin.client.widget.grid.EditorHandler.EditorRequest;
import com.vaadin.client.widgets.Grid;
import com.vaadin.client.widgets.Grid.Column;
import com.vaadin.components.common.js.JS;
import com.vaadin.components.common.js.JSArray;
import com.vaadin.components.grid.config.JSColumn;
import com.vaadin.components.grid.config.JSEditorHandler;
import com.vaadin.components.grid.config.JSEditorRequest;
import com.vaadin.components.grid.data.DataItemContainer;
import com.vaadin.components.grid.table.GridColumn;

@JsNamespace(JS.VAADIN_JS_NAMESPACE)
@JsExport
@JsType
public class GridEditor {

    private final Grid<Object> grid;
    private final GridComponent gridComponent;
    private Element container;
    private JSEditorHandler handler;

    private final Map<JSColumn, Widget> editors = new HashMap<>();

    @JsNoExport
    public GridEditor(GridComponent gridComponent) {
        this(gridComponent, (JSEditorHandler) JavaScriptObject.createObject());
    }

    @JsNoExport
    protected GridEditor(GridComponent gridComponent, JSEditorHandler handler) {
        this.gridComponent = gridComponent;
        this.grid = gridComponent.getGrid();
        this.handler = handler;
        configureGridEditor();
    }

    public void setContainer(Element containerElement) {
        this.container = containerElement;
    }

    public boolean isEnabled() {
        return grid.isEditorEnabled();
    }

    public void setEnabled(boolean enabled) {
        grid.setEditorEnabled(enabled);
    }

    public String getSaveButtonText() {
        return grid.getEditorSaveCaption();
    }

    public void setSaveButtonText(String saveButtonText) {
        grid.setEditorSaveCaption(saveButtonText);
    }

    public String getCancelButtonText() {
        return grid.getEditorCancelCaption();
    }

    public void setCancelButtonText(String cancelButtonText) {
        grid.setEditorCancelCaption(cancelButtonText);
    }

    public void editRow(int row) {
        if (grid.isEditorActive()) {
            cancel();
        }

        grid.editRow(row);
    }

    public void save() {
        grid.saveEditor();
    }

    public void cancel() {
        if (grid.isEditorActive()) {
            grid.cancelEditor();
        }
    }

    public void setHandler(JSEditorHandler handler) {
        this.handler = handler;
    }

    public JSEditorHandler getHandler() {
        return handler;
    }

    private JSEditorRequest createJSEditorRequest(
            EditorRequest<Object> request, final boolean clearEditorsOnSuccess) {
        JSEditorRequest result = JS.createJsType(JSEditorRequest.class);
        result.setRowIndex(request.getRowIndex());

        Object dataItem = request.getRow();
        if (dataItem instanceof DataItemContainer) {
            dataItem = ((DataItemContainer) dataItem).getDataItem();
        }
        result.setDataItem(dataItem);
        result.setGrid(container);
        result.setSuccess(JsUtils.wrapFunction(new Function() {
            @Override
            public void f() {
                request.success();
                if (clearEditorsOnSuccess) {
                    editors.clear();
                }
            }
        }));
        result.setFailure(JsUtils.wrapFunction(new Function() {
            @Override
            public void f() {
                JSArray<JSColumn> jsErrorColumns = arguments(1);
                Collection<Column<?, Object>> errorColumns = new ArrayList<>();
                for (GridColumn column : gridComponent.getDataColumns()) {
                    if (jsErrorColumns != null
                            && jsErrorColumns.indexOf(column.getJsColumn()) != -1) {
                        errorColumns.add(column);
                    }
                }
                request.failure(arguments(0), errorColumns);
            }
        }));
        result.setGetCellEditor(JsUtils.wrapFunction(new Function() {
            @Override
            public Object f(Object... args) {
                return getEditor(arguments(0)).getElement();
            }
        }));
        return result;
    }

    private void configureGridEditor() {
        grid.setEditorHandler(new EditorHandler<Object>() {
            @Override
            public void bind(EditorRequest<Object> request) {
                if (handler.getBind() != null) {
                    JS.exec(handler.getBind(),
                            createJSEditorRequest(request, false));
                } else {
                    for (Column<?, Object> c : grid.getColumns()) {
                        if (c.isEditable()) {
                            String value = String.valueOf(c.getValue(request
                                    .getRow()));
                            $(getWidget(c)).val(value);
                        }
                    }
                    request.success();
                }
                clearTextSelection();
            }

            @Override
            public void cancel(EditorRequest<Object> request) {
                if (handler.getCancel() != null) {
                    JS.exec(handler.getCancel(),
                            createJSEditorRequest(request, true));
                } else {
                    request.success();
                }
                editors.clear();
            }

            @Override
            public void save(EditorRequest<Object> request) {
                if (handler.getSave() != null) {
                    JS.exec(handler.getSave(),
                            createJSEditorRequest(request, true));
                    gridComponent.refresh();
                } else {
                    request.failure("'grid.editor.handler.save' is undefined. Please refer to the documentation for more information.", null);
                }
            }

            @Override
            public Widget getWidget(Column<?, Object> column) {
                return getEditor(((GridColumn) column).getJsColumn());
            }

            private native void clearTextSelection() /*-{
              if($doc.selection && $doc.selection.empty) {
                document.selection.empty();
              } else if($wnd.getSelection) {
                var sel = $wnd.getSelection();
                sel.removeAllRanges();
              }
            }-*/;
        });
    }

    private Widget getEditor(JSColumn jscol) {
        Widget w = editors.get(jscol);
        if (w == null) {
            Element e;
            if (handler.getGetCellEditor() != null) {
                e = JS.exec(handler.getGetCellEditor(), jscol);
            } else {
                e = Document.get().createTextInputElement();
                e.addClassName("v-grid style-scope");
            }
            if (e != null) {
                w = new SimplePanel(e) {
                    @Override
                    protected void onAttach() {
                        super.onAttach();
                        getElement().getParentElement().addClassName("v-grid style-scope");
                    }
                };
                editors.put(jscol, w);
            }
        }

        return w;
    }
}

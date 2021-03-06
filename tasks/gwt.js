var args = require('yargs').argv;
var cmd = require('child_process');
var fs = require('fs-extra');
var gulp = require('gulp');
var gutil = require('gulp-util');
var rename = require('gulp-rename');
var replace = require('gulp-replace');

var pwd = process.cwd();
var gwtproject = 'vaadin-components-gwt';
var version = '0.3.0';
var major = version.replace(/(\d+\.\d+\.).*$/, '$1');
var patch = ~~((new Date().getTime() / 1000 - 1420070400) / 60)
var tag =  args.version || major + patch; // will rename the tag variable in a separate patch

var component = 'vaadin-grid';
var moduleName = 'VaadinGrid';
var webDir = 'vaadin-components-gwt/src/main/webapp/';
var webComponentDir = webDir + component + '/';
var componentDir = 'vaadin-components/' + component;

function system(command, cb) {
  cmd.exec(command, function(err, stdout, stderr) {
    if (err) {
      gutil.log(stderr);
      throw err;
    }
    if (cb) {
      cb(err);
    }
  });
};

gulp.task('gwt:compile', ['gwt:clean-maven'], function(done) {
  if(args.gwtSkipCompile) {
    done();
    return;
  }

  gutil.log('Updating Maven dependencies ...');
  system('mvn compile -q -am -pl ' + gwtproject, function() {
    gutil.log('Compiling GWT components ...');
    var command = 'mvn package -q -am -pl ' + gwtproject + (args.gwtPretty ? ' -Ppretty' : '') + " -P compile";
    system(command, function() {
      gutil.log('GWT components compilation succeeded.')

      done();
    });
  });
});

gulp.task('gwt:copy', ['gwt:copy-files', 'gwt:copy-imports', 'gwt:copy-deferred']);

gulp.task('gwt:clean-maven', function(done) {
  if(args.gwtSkipClean) {
    done();
    return;
  }

  system('mvn clean', done);
});

gulp.task('test:gwt', function(done) {
  cmd.exec('mvn test', function(err, stdout) {
    gutil.log(stdout);
    done();
  });
});

gulp.task('watch:gwt', function() {
  var paths = ['vaadin-components-gwt/src/main/webapp/vaadin-grid/**/*'];

  gulp.watch(paths, ['gwt']);
});

gulp.task('clean:gwt', ['gwt:clean-maven'], function() {
  if(args.gwtSkipClean) {
    return;
  }

  fs.removeSync(componentDir);
  fs.mkdirsSync(componentDir);
});

gulp.task('gwt:copy-files', ['gwt:compile', 'clean:gwt'], function() {
  return gulp.src(webComponentDir + '**/*')
          .pipe(replace(new RegExp('^.*script.*\.\./\.\..*' + moduleName + '.*'+ moduleName +'.*$','mg'), '  <link rel="import" href="../'+component+'.html">'))
          .pipe(replace(new RegExp('^.*script.*\.\..*' + moduleName + '.*'+ moduleName +'.*$','mg'), '  <link rel="import" href="'+component+'.html">'))
          .pipe(replace(/(src|href)=("|')([\.\.\/]*)\/bower_components\//mg, '$1=$2$3/../bower_components/'))
          .pipe(gulp.dest(componentDir));
});

gulp.task('gwt:copy-imports', ['gwt:compile', 'clean:gwt'], function() {
  var warDir = 'vaadin-components-gwt/target/vaadin-components-gwt-' + version + '/';
  var modulePath = warDir + moduleName + 'Import/';
  return gulp.src(modulePath + moduleName +'Import.nocache.js').pipe(gulp.dest(componentDir));
});

gulp.task('gwt:copy-deferred', ['gwt:compile', 'clean:gwt'], function() {
  return gulp.src(componentDir + 'deferred')
          .pipe(gulp.dest(componentDir));
});

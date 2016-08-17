"use strict"
const gulp = require('gulp');
const yaml = require('gulp-yaml');

const CFN_SOURCE = 'cloudformation/**/*.yml';
const CFN_DESTINATION = 'cloudformation';

gulp.task('cloudformation', () => {
	return gulp.src(CFN_SOURCE)
		.pipe(yaml({ space: 4 }))
		.pipe(gulp.dest(CFN_DESTINATION));
});

gulp.task('cfn', ['cloudformation']);

gulp.task('cloudformation-dev', ['cloudformation'], () => {
	gulp.watch(CFN_SOURCE, ['cloudformation']);
});

gulp.task('default', ['cloudformation']);

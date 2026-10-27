hold on
format compact
warning off all
import java.io.*;
import geometry.*
addpath ../lib/common
ls *.m
disp 'hello, world'
mex -O file.c
x = 1;
y = x';
z = [x y];
disp(x)
x

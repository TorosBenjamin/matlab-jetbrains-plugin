function d = distance(p1, p2)<fold text='...'>
%DISTANCE Euclidean distance between two points.
%   More help.
import geometry.*
d = hypot(p2.X - p1.X, p2.Y - p1.Y);
</fold>end

if d > 1 % trailing comment stays<fold text='...'>
    d = 1;
</fold>end

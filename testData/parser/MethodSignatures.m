classdef Shape
    methods (Access = protected)
        h = describe(obj)
        [a, b] = pair(obj, x)
        reset(obj)
        refresh
    end
    methods (Static)
        function s = unit()
            s = Shape();
        end
        out = fromFile(name);
    end
end

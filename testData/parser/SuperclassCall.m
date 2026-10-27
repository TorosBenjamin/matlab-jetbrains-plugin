classdef Child < pkg.Base
    methods
        function obj = Child(a)
            obj@pkg.Base(a);
        end
        function names = vars(obj)
            names = horzcat( vars@pkg.Base(obj), ...
                {'x', 'y'} );
            B = linop( vertcat@chebmatrix(names{:}) );
        end
    end
end

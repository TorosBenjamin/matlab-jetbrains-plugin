classdef ClassMethods
    properties
        Width
    end
    methods
        function publicMethod(obj)
            obj.usedPrivate();
        end
    end
    methods (Access = private)
        function usedPrivate(obj)
        end
        function <weak_warning descr="Private method 'unusedPrivate' is never used">unusedPrivate</weak_warning>(obj)
        end
        function set.Width(obj, v)
        end
    end
end

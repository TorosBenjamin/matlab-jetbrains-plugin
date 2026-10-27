classdef Shape < handle & matlab.mixin.Copyable
    %SHAPE Example handle class with enums-ish property and events.
    properties
        Width (1,1) double = 1
        Height (1,1) double = 1
    end
    properties (SetAccess = private)
        Kind string = "rect"
    end
    properties (Constant)
        Version = 2
    end
    events
        Resized
    end
    methods
        function obj = Shape(w, h)
            if nargin > 0
                obj.Width = w;
                obj.Height = h;
            end
        end
        function a = area(obj)
            a = obj.Width * obj.Height;
        end
        function resize(obj, factor)
            obj.Width = obj.Width * factor;
            obj.Height = obj.Height * factor;
            notify(obj, 'Resized');
        end
        function set.Width(obj, v)
            obj.Width = abs(v);
        end
    end
    methods (Static)
        function s = unitSquare()
            s = Shape(1, 1);
        end
    end
    methods (Access = protected)
        h = describe(obj)   % signature only
    end
end

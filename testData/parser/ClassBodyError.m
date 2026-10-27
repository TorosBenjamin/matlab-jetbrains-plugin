classdef AppConfig < handle
    methods (Static)
        function obj = getInstance()
        end
    end
    make_point()
    methods
        function enableVerbose(obj)
            obj.Verbose = true;
        end
    end
end

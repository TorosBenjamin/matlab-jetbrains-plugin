classdef AppConfig < handle
    %APPCONFIG Classic MATLAB singleton (persistent variable pattern).
    %   cfg = AppConfig.getInstance() always returns the same object.
    properties
        Verbose logical = false
        OutputDir char = 'out'
    end
    methods (Access = private)
        function obj = AppConfig()
        end
    end
    methods (Static)
        function obj = getInstance()
            %GETINSTANCE Return the shared AppConfig object.
            persistent instance
            if isempty(instance) || ~isvalid(instance)
                instance = AppConfig();
            end
            obj = instance;
        end
    end
    methods
        function enableVerbose(obj)
            %ENABLEVERBOSE Turn on verbose logging.
            obj.Verbose = true;
        end
    end
end

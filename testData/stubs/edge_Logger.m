classdef Logger < handle
    %LOGGER Singleton variant using an output arguments block (R2022b+).
    properties
        Lines string = string.empty
    end
    methods (Static)
        function obj = instance()
            arguments (Output)
                obj Logger
            end
            persistent inst
            if isempty(inst)
                inst = Logger();
            end
            obj = inst;
        end
    end
    methods
        function log(obj, msg)
            %LOG Append a message.
            obj.Lines(end+1) = msg;
        end
    end
end

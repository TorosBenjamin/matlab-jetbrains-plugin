function [result, varargout] = add_numbers(a, b, options)
arguments
    a double
    b double = 0  % default
    options.Scale (1,1) double {mustBePositive} = 1
    options.Name string = "x"
end
arguments (Output)
    result double
end
result = (a + b) * options.Scale;
end

function r = uses_arguments_variable()
arguments = {1, 2};
r = arguments(1);
end

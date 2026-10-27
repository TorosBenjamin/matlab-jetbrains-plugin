function [result, varargout] = add_numbers(a, b, options)
%ADD_NUMBERS Add two numbers.
%   result = add_numbers(a, b) returns a + b.
arguments
    a double
    b double = 0
    options.Scale (1,1) double {mustBePositive} = 1
end
result = (a + b) * options.Scale;
if nargout > 1
    varargout{1} = result * 2;
end
end

if ~logger.manager('write_to_console') ...   %% and active logger is NOT
    msglev = 0;
end
while x > 0 ... comment
    x = x - 1;
end
switch s(1).subs,
    case 'a',
        y = 1;
end
[lineStyle, pointStyle, out] = ...
    parsePlotStyle(varargin)
opt = struct( ...
    );

% main.m - entry script. Run this to exercise everything.
%   Go-to-definition targets: add_numbers, helper_local, geometry.Point,
%   geometry.distance, Shape, secret_helper (private/).

%% Section 1: basics
x = 3;
y = add_numbers(x, 4);          % function in its own file
fprintf('add_numbers: %d\n', y);

%% Section 2: transpose vs string quote
A = [1 2; 3 4];
B = A';                          % transpose
C = A'';                         % double transpose
s = 'it''s a char array';        % escaped quote inside char
t = "string with ""quotes""";    % string type
D = A.' * B';                    % non-conjugate transpose
v = {A', 'a', "b"}';             % transpose after cell + strings inside
disp(s); disp(t);

%% Section 3: whitespace inside brackets
r1 = [1 -2];        % two elements
r2 = [1 - 2];       % one element
r3 = [1 -2 + 3];    % two elements: 1 and 1
r4 = [x' y'];       % two transposes
r5 = [A (1:2)'];    % A and a column, not A indexed
fprintf('numel r1=%d r2=%d r3=%d\n', numel(r1), numel(r2), numel(r3));

%% Section 4: end as keyword and as index
z = 1:10;
last = z(end);
mid = z(end/2:end-1);
for k = 1:numel(z)
    if mod(k, 2) == 0 && k ~= z(end)
        continue
    elseif k > 7
        break
    end
end

%% Section 5: command syntax
format compact
hold_state = 'off';  % (no figure needed)
warning off all
warning on all

%% Section 6: comments
%{
Block comment.
x = this is not code
%}
q = 1; % trailing comment with 'quote' and "dq"
w = 2 ... continuation comment here
    + 3;

%% Section 7: anonymous functions, cell arrays, structs
sq = @(n) n.^2;
fh = @add_numbers;
cellfun(@(c) disp(class(c)), {1, 'a', {}});
st.name = 'test';
st.('dynamic') = 5;
st(2).name = 'second';
vals = {st.name};

%% Section 8: packages and classes
p = geometry.Point(1, 2);
p2 = geometry.Point(4, 6);
d = geometry.distance(p, p2);
fprintf('distance: %.2f\n', d);
p = p.move(1, 1);
sh = Shape.unitSquare();
fprintf('area: %.2f, kind: %s\n', sh.area(), char(sh.Kind));
leg = geometry.Legacy(5);
disp(value(leg));

%% Section 9: local function + private function + try/catch + switch
out = helper_local(3);
sec = secret_helper(2);
try
    error('demo:id', 'Expected error %d', 1);
catch err
    fprintf('caught: %s\n', err.identifier);
end
switch class(out)
    case {'double', 'single'}
        disp('numeric');
    otherwise
        disp('other');
end
while false, end
unwind = onCleanup(@() disp('cleanup ran'));

%% Section 9b: type inference through singletons and factories
cfg = AppConfig.getInstance();
cfg.enableVerbose();            % completion/go-to should know cfg is AppConfig
fprintf('verbose: %d\n', cfg.Verbose);
lg = Logger.instance();
lg.log("hello");                % lg is Logger (declared in arguments (Output))
mp = make_point(1, 2);
mp = mp.move(1, 1);             % mp is geometry.Point (inferred from factory body)
same = AppConfig.getInstance();
assert(same == cfg);            % handle equality: same object

%% Section 10: local functions in script
function r = helper_local(n)
    r = nested_outer(n) + 1;
end

function r = nested_outer(n)
    base = 10;
    r = inner(n);
    function out = inner(m)   % nested function sees base
        out = base + m;
    end
end

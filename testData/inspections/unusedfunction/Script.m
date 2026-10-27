x = used(1);
set(0, 'Callback', 'byCallback');
function r = used(n)
    r = n;
end
function r = <weak_warning descr="Local function 'unused' is never used">unused</weak_warning>(n)
    r = n;
end
function byCallback()
end

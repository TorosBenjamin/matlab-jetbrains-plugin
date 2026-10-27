function r = nested_outer(n)
    base = 10;
    later = 5;
    <weak_warning descr="Variable 'unused' is never used">unused</weak_warning> = 1;
    r = inner(n);
    function out = inner(m)
        out = base + m + later;
    end
end

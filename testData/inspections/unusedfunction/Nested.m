function outer()
    inner();
    function inner()
    end
    function <weak_warning descr="Nested function 'neverCalled' is never used">neverCalled</weak_warning>()
    end
end

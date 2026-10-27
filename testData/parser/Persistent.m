function obj = getInstance()
persistent instance count
if isempty(instance)
    instance = 1;
end
obj = instance;
end

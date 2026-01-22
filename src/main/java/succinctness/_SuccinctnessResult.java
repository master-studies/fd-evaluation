package succinctness;

public class _SuccinctnessResult {
    
    public _FunctionalDependencyGroup fd;
    public int length;

    public _SuccinctnessResult(_FunctionalDependencyGroup fd, int length) {
        this.fd = fd;
        this.length = length;
    }
}

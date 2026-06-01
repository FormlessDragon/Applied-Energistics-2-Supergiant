package ae2.requester.status;

public final class CpuState extends BlockingState {
    @Override
    public RequestStatus type() {
        return RequestStatus.CPU;
    }
}

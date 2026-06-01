package ae2.requester.status;

public final class MissingState extends BlockingState {
    @Override
    public RequestStatus type() {
        return RequestStatus.MISSING;
    }
}

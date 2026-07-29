package bm.b0b0b0.soulAuction.model.result;

public record CancelResult(boolean success, boolean movedToClaim, CancelFailure failure) {

    public static CancelResult success(boolean movedToClaim) {
        return new CancelResult(true, movedToClaim, null);
    }

    public static CancelResult failure(CancelFailure failure) {
        return new CancelResult(false, false, failure);
    }
}

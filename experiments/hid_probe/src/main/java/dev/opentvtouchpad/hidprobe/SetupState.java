package dev.opentvtouchpad.hidprobe;

/** Configuration and ephemeral presentation only; never grants a remote input epoch. */
final class SetupState {
    enum Page {
        OVERVIEW, BT_START, BT_WAIT, BT_COMPARE, BT_CLOSING, BT_DONE,
        ADDRESS, TV_START, TV_CONNECTING, TV_CODE, TV_CLOSING, TV_ERROR,
        DONE, MANAGE, CLOSING, RECOVERY
    }

    enum Action {
        START, BT_PAIR, BT_CONFIRM, BT_MISMATCH, NEXT, SAVE_ADDRESS,
        PAIR_TV, CONFIRM_TV, RETRY, CHANGE_ADDRESS, BACK, CANCEL, MANAGE,
        OPEN_REMOTE, REPAIR_BT, REPAIR_TV, FORGET_BT, FORGET_TV, APPEARANCE,
        BATTERY_SETTINGS
    }

    enum Status { DONE, NEXT, TO_DO }

    record Snapshot(Page page, boolean bluetoothSaved, boolean addressSaved,
            boolean tvPaired, String address, String message, String diagnostic,
            long remainingMillis, boolean busy, boolean enabled, long generation,
            boolean requestDiscoverable) {
        boolean complete() { return bluetoothSaved && addressSaved && tvPaired; }
        boolean anyComplete() { return bluetoothSaved || addressSaved || tvPaired; }

        Page firstMissing() {
            if (!bluetoothSaved) return Page.BT_START;
            if (!addressSaved) return Page.ADDRESS;
            if (!tvPaired) return Page.TV_START;
            return Page.MANAGE;
        }

        Status status(int step) {
            boolean saved = switch (step) {
                case 1 -> bluetoothSaved;
                case 2 -> addressSaved;
                case 3 -> tvPaired;
                default -> throw new IllegalArgumentException("Invalid setup step");
            };
            if (saved) return Status.DONE;
            boolean next = switch (step) {
                case 1 -> true;
                case 2 -> bluetoothSaved;
                default -> bluetoothSaved && addressSaved;
            };
            return next ? Status.NEXT : Status.TO_DO;
        }
    }

    private SetupState() {}
}

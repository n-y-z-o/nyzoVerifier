package co.nyzo.verifier;

import co.nyzo.verifier.util.TestnetUtil;

public class BlockchainVersionManager {

    // This class can be reused for other blockchain versions in the future. It will always attempt to upgrade the
    // blockchain to the latest available version.

    public static final long activationHeight = TestnetUtil.testnet ? 100L : 4_500_000L;

    public static boolean upgradePending(Block frozenEdge) {

        // Upgrade if the current version is less than the maximum version, if the height is at least the activation
        // height, and if the height is divisible by 50. The final condition keeps the cycle from going through a
        // multi-pass consensus process every block if not enough of the cycle is prepared to upgrade yet.
        long targetBlockHeight = frozenEdge.getBlockHeight() + 1L;
        return frozenEdge.getBlockchainVersion() < Block.maximumBlockchainVersion &&
                targetBlockHeight >= activationHeight &&
                targetBlockHeight % 50L == 0;
    }

    public static boolean isMissedUpgradeOpportunity(Block block, int previousBlockVersion) {

        // This is only used to lightly penalize blocks that are not upgrades when an upgrade is allowed. This gives
        // preference to the upgrade block, if provided.
        return block.getBlockchainVersion() == previousBlockVersion &&
                block.getBlockchainVersion() < Block.maximumBlockchainVersion && block.getBlockHeight() % 50L == 0;
    }

    public static boolean isImproperlyTimedUpgrade(Block block, int previousBlockVersion) {

        // This is used to penalize upgrade blocks that are submitted at non-preferred times.
        return block.getBlockchainVersion() > previousBlockVersion && block.getBlockHeight() % 50L != 0;
    }
}

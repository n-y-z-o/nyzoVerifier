package co.nyzo.verifier;

import co.nyzo.verifier.util.TestnetUtil;

public class BlockchainVersionManager {

    // This class can be reused for other blockchain versions in the future. It will always attempt to upgrade the
    // blockchain to the latest available version.
    private static final long[] activationHeightsTestnet = { 0L, 100L, 200L };
    private static final long[] activationHeightsProduction = { 0L, 4_500_000L, 7_000_000L };

    private static long activationHeight(int version) {
        long[] activationHeights = TestnetUtil.testnet ? activationHeightsTestnet : activationHeightsProduction;
        return version >= 0 && version < activationHeights.length ? activationHeights[version] : Long.MAX_VALUE;
    }

    public static boolean upgradePending(Block frozenEdge) {

        // Upgrade if the current version is less than the maximum version, if the height is at least the activation
        // height, and if the height is divisible by 50. The final condition keeps the cycle from going through a
        // multi-pass consensus process every block if not enough of the cycle is prepared to upgrade yet.
        long targetBlockHeight = frozenEdge.getBlockHeight() + 1L;
        return frozenEdge.getBlockchainVersion() < Block.maximumBlockchainVersion &&
                targetBlockHeight >= activationHeight(frozenEdge.getBlockchainVersion() + 1) &&
                targetBlockHeight % 50L == 0;
    }

    public static boolean isMissedUpgradeOpportunity(Block block, int previousBlockVersion) {

        // This is only used to lightly penalize blocks that are not upgrades when an upgrade is allowed. This gives
        // preference to the upgrade block, if provided.
        return block.getBlockHeight() >= activationHeight(block.getBlockchainVersion() + 1) &&
                block.getBlockchainVersion() == previousBlockVersion &&
                block.getBlockchainVersion() < Block.maximumBlockchainVersion && block.getBlockHeight() % 50L == 0;
    }

    public static boolean isImproperlyTimedUpgrade(Block block, int previousBlockVersion) {

        // This is used to penalize upgrade blocks that are submitted at non-preferred times.
        return block.getBlockchainVersion() > previousBlockVersion &&
                (block.getBlockHeight() < activationHeight(block.getBlockchainVersion()) ||
                        block.getBlockHeight() % 50L != 0);
    }
}

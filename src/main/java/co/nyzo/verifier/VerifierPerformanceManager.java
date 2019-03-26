package co.nyzo.verifier;

import co.nyzo.verifier.messages.BlockVote;
import co.nyzo.verifier.util.FileUtil;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

public class VerifierPerformanceManager {

    // All scores start at zero. For consistency with chain scores, a lower score is considered superior to a higher
    // score. The 3:6 increment-to-decrement ratio means that, with 75% consensus, the cycle will see score reductions
    // on average for each block frozen.
    private static final int perBlockIncrement = 3;
    private static final int perVoteDecrement = -6;
    private static final int removalThresholdScore = 12343 * 2 * perBlockIncrement;  // two days from 0
    private static final int minimumScore = -removalThresholdScore;  // up to two additional days for good performance

    private static final Map<ByteBuffer, Integer> verifierScoreMap = new ConcurrentHashMap<>();
    private static AtomicInteger blocksSinceWritingFile = new AtomicInteger();

    public static final File scoreFile = new File(Verifier.dataRootDirectory, "performance_scores");

    private static final BiFunction<Integer, Integer, Integer> mergeFunction =
            new BiFunction<Integer, Integer, Integer>() {
                @Override
                public Integer apply(Integer integer0, Integer integer1) {
                    int value0 = integer0 == null ? 0 : integer0;
                    int value1 = integer1 == null ? 0 : integer1;
                    return Math.max(minimumScore, value0 + value1);
                }
            };

    static {
        loadPersistedScores();
    }

    public static void updateScoresForFrozenBlock(Block block, Map<ByteBuffer, BlockVote> votes) {

        // Only proceed if the block is not null. It is rare or maybe impossible for the block to be null, but it is
        // still a reasonable precaution in an environment such as this.
        if (block != null) {

            // Add for each in-cycle verifier. Each time a block is frozen, a verifier's score increases, but it then
            // decreases for each vote received.
            Set<ByteBuffer> inCycleVerifiers = BlockManager.verifiersInCurrentCycleSet();
            for (ByteBuffer verifierIdentifier : inCycleVerifiers) {
                verifierScoreMap.merge(verifierIdentifier, perBlockIncrement, mergeFunction);
            }

            // Subtract for each vote for hash of the block that was frozen. These are the votes that helped the
            // blockchain reach consensus.
            for (ByteBuffer verifierIdentifier : votes.keySet()) {
                BlockVote vote = votes.get(verifierIdentifier);
                if (ByteUtil.arraysAreEqual(vote.getHash(), block.getHash())) {
                    verifierScoreMap.merge(verifierIdentifier, perVoteDecrement, mergeFunction);
                }
            }

            // Every 30 blocks, clean up the map and write it to file. We only track scores for in-cycle verifiers.
            // This increment/check/reset of the count is not strictly thread-safe, but failure would only cause the
            // file to be written an extra time. Checking that the cycle is complete ensures we don't clear in-cycle
            // verifiers from the map due to missing cycle information in the block manager.
            if (blocksSinceWritingFile.incrementAndGet() >= 30 && BlockManager.isCycleComplete()) {
                blocksSinceWritingFile.set(0);

                // Remove all out-of-cycle verifiers from the map.
                for (ByteBuffer verifierIdentifier : new HashSet<>(verifierScoreMap.keySet())) {
                    if (!BlockManager.verifierInCurrentCycle(verifierIdentifier)) {
                        verifierScoreMap.remove(verifierIdentifier);
                    }
                }

                // Write the map to the file.
                persistScores();
            }
        }
    }

    private static void persistScores() {

        List<String> lines = printScores();
        Path path = Paths.get(scoreFile.getAbsolutePath());
        FileUtil.writeFile(path, lines);
    }

    private static void loadPersistedScores() {

        // This method is called in the class's static block. We load any scores that were previously saved to disk so
        // that scores do not reset each time the verifier is reloaded.
        Path path = Paths.get(scoreFile.getAbsolutePath());
        try {
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                line = line.trim();
                int indexOfHash = line.indexOf("#");
                if (indexOfHash >= 0) {
                    line = line.substring(0, indexOfHash).trim();
                }
                String[] split = line.split(",");
                if (split.length == 2) {
                    try {
                        byte[] identifier = ByteUtil.byteArrayFromHexString(split[0].trim(), FieldByteSize.identifier);
                        int score = Integer.parseInt(split[1].trim());
                        verifierScoreMap.put(ByteBuffer.wrap(identifier), score);
                    } catch (Exception ignored) { }
                }
            }

            System.out.println("loaded " + verifierScoreMap.size() + " scores from file");
        } catch (Exception ignored) { }
    }

    public static List<String> printScores() {

        // Scores are written one per line: verifier, followed by identifier. For ease of reading, they are sorted
        // high (bad) to low (good) so that the verifiers that are most in danger of penalties are at the top of the
        // list.
        List<ByteBuffer> identifiers = new ArrayList<>(verifierScoreMap.keySet());
        Collections.sort(identifiers, new Comparator<ByteBuffer>() {
            @Override
            public int compare(ByteBuffer identifier1, ByteBuffer identifier2) {
                Integer score1 = verifierScoreMap.getOrDefault(identifier1, 0);
                Integer score2 = verifierScoreMap.getOrDefault(identifier2, 0);
                return score2.compareTo(score1);
            }
        });

        List<String> lines = new ArrayList<>();
        for (ByteBuffer identifier : identifiers) {
            int score = verifierScoreMap.getOrDefault(identifier, 0);
            lines.add(String.format("%s, %5d  # %s", ByteUtil.arrayAsStringWithDashes(identifier.array()), score,
                    NicknameManager.get(identifier.array())));
        }

        return lines;
    }
}

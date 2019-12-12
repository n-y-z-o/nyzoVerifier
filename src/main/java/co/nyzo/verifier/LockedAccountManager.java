package co.nyzo.verifier;

import co.nyzo.verifier.client.CommandOutput;
import co.nyzo.verifier.client.CommandOutputConsole;
import co.nyzo.verifier.client.ConsoleUtil;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LockedAccountManager {

    // Initially, these accounts were planned to be extracted from the first block dynamically by each verifier.
    // However, this would have added complexity to the loading process and would unlikely be useful for other
    // Nyzo-based blockchains, as it is a specific solution to specific concerns about this blockchain.

    // For anyone wishing to confirm this list, please consult block 1. There are 459 transfers with empty sender data
    // in that block. One of those transfers is μ2 to account 0000...0001. Another is for creation of the seed account.
    // The accounts created by the other 457 transfers are all included here.

    // Also, to further reduce concerns about coins controlled by the Nyzo team being a potential source of market
    // destabilization, the official Nyzo verifiers have been added to the locked accounts. As we have stated many,
    // many times, there is nothing special about these verifiers other than their early presence in the blockchain.
    // However, due to that early presence, they do control a large sum of coins. Subjecting these coins to the lock
    // threshold will provide an additional assurance of stability.

    // For completeness, we are also including the two Argo verifiers and killr in the locked set. Those who read the
    // Discord conversation history will know that these are controlled by members of the Nyzo team. We have a few other
    // verifiers, but their coin totals are not substantial (<∩60,000, and we would prefer not to publicly reveal our
    // control of those verifiers.

    private static final List<ByteBuffer> lockedAccountList = Arrays.asList(

            // These are the official Nyzo verifiers.
            id("b5fd3e8d789a5055-091e46db881f1b74-1b0ab6f8d65b21ae-88cc543dfd92173b"),  // Nyzo 0
            id("15fa0cd9b1619538-58d097090621a4de-24063449b66d4dac-2af90543389a9f89"),  // Nyzo 1
            id("4ddefde6a0c5abf7-8868f2c13803f934-c45a0f675f69fd75-0d6578046617eec5"),  // Nyzo 2
            id("1459eed3a8d3bbf1-d1faaf553f086033-6615d10e0ebd44b5-f8b5c94418457a43"),  // Nyzo 3
            id("684d8b1bfedb0bb3-19954ba3e330d825-77ed7ffa45fdf468-cfe01accac6db89d"),  // Nyzo 4
            id("e917bf3cf77b2c8e-100a3715500397ab-cb89f99963a174e1-3c5b4e11cbb72852"),  // Nyzo 5
            id("f83cf2e0e3abc5df-01bddd67fead3099-bff7efaf467010f5-3b654f293a9a9887"),  // Nyzo 6
            id("363a10a67dfac9a2-ac59dc7a1fd03e37-05665f01560d399c-78a367dc21ce94ce"),  // Nyzo 7
            id("de2fd26165e1b774-ce8da5365040fc60-be84a2167e1d62e7-f847b40fea05863a"),  // Nyzo 8
            id("92a5849feebbb2e1-fb64b6d93940bad3-6168ff0d4f984f1a-7b64dc6fedc0dae5"),  // Nyzo 9

            // These are the other verifiers publicly known to be controlled by members of the Nyzo team.
            id("c34a6f1942cb7ec1-0d2a440b3e116041-d05df2746ebe7b41-802340a1495e7af5"),  // Argo 746
            id("7cd4b6c2bd8316e4-40b0a6affb2c78dc-b5d7fdaa17aae8c6-a9bd45ac10017f9d"),  // Argo 752
            id("24e850c8cf5b0d38-7af739fde5c5df12-7f788551c7d5d438-895d7b3b10e82b63"),  // killr

            // This is the Genesis account followed by the block-1 distribution accounts in transaction order.
            id("64afc20a4a4097e8-494239f2e7d1b1db-de59a9b157453138-f4716b72a0424fef"),  // Genesis
            id("a93dc2b6a15396a9-a64f46b426508032-de56e6877cb8dcec-10698d7e08575eea"),  // 0
            id("a9ace0c24837000b-3c34ffbdf459d2b9-5924214dd270a870-9c44685ccc7ed2e1"),  // 1
            id("1a848b1c8d6f452e-675b13af5e532a55-c385e612006ecba0-fc4202f4a6d0691d"),  // 2
            id("f29f1a0dd968d3c2-ac0e60bf0e7f51aa-34a314760529aa31-5c3515fc2358f8f9"),  // 3
            id("11156c683a2f814b-36bc85f855e3a042-40b28624ccb80365-886258cb29c86db2"),  // 4
            id("d2ef7bd47dc01d5e-53eb5356c34604d9-a512d2d437dc12fe-bf037cfa5283b869"),  // 5
            id("2f86ca7621eaf858-ed82664f2eada0bd-927cad19ee085c5b-b4db9f075c39b693"),  // 6
            id("98acc91a55885b8a-f4909b6b67582f85-21e59f917e957102-d0a2a76e55aa4f2a"),  // 7
            id("94154939d6db25e7-88a46b1b5a055024-86d30289f3e860aa-c3eb08689a002316"),  // 8
            id("aa91822ca7417949-af226b80d80d07f4-f22dd5528d1e1ccd-f7e4770fc3313f7a"),  // 9
            id("0bacb88a08557448-59005a84afd296c2-01d8648f3d65c39e-ce01407425c9a49f"),  // 10
            id("419add6ba0ec4a91-dba24487fc95c59f-46255c107a523aff-bed3539020e3aa9c"),  // 11
            id("15f4abec9cc350b7-3b3d3e7858b29f2f-ac62f273254151d4-45988c0f80aaaad0"),  // 12
            id("9f4b3dc7c758a218-b102f0756f3d8599-1a2a1f449b712339-f10c39f35d83e958"),  // 13
            id("bb2a3cfedefbe25c-288109f30cd31c09-90eb0fbee7c045f6-adb57d66f2a328f7"),  // 14
            id("2bc755fbe20b19a5-cf7ad7a3824a2f4f-d333f437fb57d457-9c7c8756ef703b75"),  // 15
            id("84235b991d17be29-af8f99980dc035ec-db0496ddd1722df3-07fb132132bcdefb"),  // 16
            id("c3ca0d9c25708a30-df5915fa6e5e9258-e7350357b6c2078e-63de5b390dde92fb"),  // 17
            id("fd19430db0f60dd5-dbe4f314e7054a83-1fe3b3a630461436-28a97f06222e8a8e"),  // 18
            id("e01bdd1586606ac4-1843a23e06f27892-fd359fe47218196b-35bb7dbbd7bec6f5"),  // 19
            id("860919291fbdda2d-5097535b2ad73515-61ee8df066d9e641-21cf495054d94c06"),  // 20
            id("d6ff5bf16040293b-b6a8167d983ad04a-c8b437881674685b-522b21a2801393c3"),  // 21
            id("b0f72e9a1ba76931-74b640207580dd5d-0c2e341e75e3c096-67000c27c4cf0bf8"),  // 22
            id("fe4c43f8536b86f8-f15fe464d799624f-e7e4034708720769-89f52429e357b426"),  // 23
            id("d2130b43659676a2-30ea5b4896cd95f3-03df339f4655521c-325843be86a5e101"),  // 24
            id("bfd2ff17fbe2243e-fc7a1298d94bfc2a-6325187b752853e2-07ae8c432b9d6f28"),  // 25
            id("b4847afe3f5ce962-c5016276bd49cb6c-c16a5148b19ddc98-6734f52139ef11db"),  // 26
            id("8d0e4442d4374956-aa9f76429fd90b57-4bb5396c4631a651-8b27fec38ce49bde"),  // 27
            id("56e0759ab29d1ffb-c93d966c2a7221de-acdf1d9854303f33-c775d7402b17af72"),  // 28
            id("18afdbc79ad02626-045b810931ce1260-484bf9d6cdba5331-fec52f61314253bd"),  // 29
            id("8bcb457af132e9f9-1dfbcbfd7fa94f73-8551edca55139b1c-6cdac22e28a73757"),  // 30
            id("5e9354a81df759db-cc75d781c6cdc7e2-cd3db1e50639ae33-45c8031eb3f6d536"),  // 31
            id("2b9c3f1c95a4e9d8-09492947fd30a155-0bf634dd7078d8a7-010c9233417d2d14"),  // 32
            id("eae8139079854796-676ba2736dfc40d5-7350eafc73b30e4f-4d54a5d5da75bb07"),  // 33
            id("56c6a6f89b76c8db-83c7697acccddc86-491b85dd68d5eb6a-d76863f9f131b559"),  // 34
            id("e8f0650f330c70af-f3fe93321feb9473-ee8625529e09fd0d-dcc0c6c497bc73e4"),  // 35
            id("c43cca6272823e82-c6872181aaf08317-a668ba9ffa8634e9-4dc407a3af3393fc"),  // 36
            id("b6f040c197f60995-55434922f677e7c4-5d65a30ccf2fb409-7fa7782e811ada6a"),  // 37
            id("0e91158b071210d3-911ca802a7ee333c-e9077d447c6729fd-79b793dab5728066"),  // 38
            id("ed575fd534d69942-8065c8cc3cb352c3-dd27c6f5bda8e379-8922ee5e268fbc1b"),  // 39
            id("ae5e3adef103de6a-6ea3910115c6e188-cb8d80bae972a2d4-23cda6d334cf4324"),  // 40
            id("caaa1360d850435b-f3205cd78feb2f0b-66c9d1405adf26a9-03be653ff22c08b2"),  // 41
            id("4404d216c7403e65-f237b0b320b59227-12a78a9fc5c7834e-1e25a21d69f8ae29"),  // 42
            id("81ffd64190c33175-27702127ce585739-f4b8c148431592bf-f6a8683b39a62f73"),  // 43
            id("6910d1c47fed87b2-75019450b633cd4f-6800783f8c2343f4-382dfe2e55926d7e"),  // 44
            id("9318b1f0092a0452-0e69a4b8428963cd-143ab92d5125852c-79e45a464fb7e67c"),  // 45
            id("2f80aa801ce2d58a-f45d7fd5a6441df6-fc377223c1a6779f-025e2429ade56b54"),  // 46
            id("8bdbcb6df832e8b1-39c489e792c82233-ea8201b7288630b0-45d5efc219dacfec"),  // 47
            id("e9d1a2e8c3c122ff-69734cfc75e0f302-ea5f0996bc703504-5641e762e24f2b93"),  // 48
            id("eafe7169c2e2038b-b0f17987f69e0ed3-8e231b6a7ee012e4-af45d4e6bdeb78f7"),  // 49
            id("90e0bc1f52ef5eff-b6f56a643141e9c7-5d4637b6b8143ce3-9ac14aa57b1785c7"),  // 50
            id("1ed654ca03e5f6f7-404ff8d16dcae9f8-9d78b4faa37a1877-1c36d1221b15e9a3"),  // 51
            id("643dbb26c7566871-457fd47e4a9ffdf4-25a1291ff482a6e2-ea10866d19e99b89"),  // 52
            id("bee0dd9826dc5cf9-1607ea7c87713d4e-dd1d06f9d628b81a-a98bee7f44e3e962"),  // 53
            id("b8a7364ec79ed3d7-58b6d4994814cb8c-b6e5cff790be3417-3c76fd2902aa69c1"),  // 54
            id("0689949f109e2061-c2d3542be019e0db-df20085bcee11bca-0d517dde9fa6b17c"),  // 55
            id("dd9a7157a0863d07-efe693d412759369-ab7754fed89787ef-f1b0498a813f8fa0"),  // 56
            id("318d6999cb6098be-4f05b9f65e69becc-d93bfb402a28610f-a42058588e7b8a56"),  // 57
            id("c363ddbf5a9f7275-6824792f09f4e392-f3a33cfe7b09868d-eacbfd41d2d7c5eb"),  // 58
            id("d1597780e71ada4f-248f71f0dafd85d2-255f3ed21ef14a66-09d56459f1591211"),  // 59
            id("a412aec6b550bc06-6453b9dbdd05953b-c0855ecf9b38eee7-1723cc7035570e7d"),  // 60
            id("7f7ea7859e1513ff-c414e6734255797b-12a9b517e2a205fc-a188d2f0a58ecadd"),  // 61
            id("b06d589817a0de7f-e5ca507075e31b9e-848982469923e6ec-fa0681717208ca48"),  // 62
            id("4ca7dc17dc8e57f6-19c2b828d9ad41e6-75152508988791c9-dd9690ffeb4ffd5c"),  // 63
            id("a2303ffe74c528fb-042210e3230dd022-dcbfd3cb8883278c-46e04814e53bc51b"),  // 64
            id("6b94242b8dd2b050-692bcc0677f42ae6-3a1291f283f0281d-cb37cbeb4480b721"),  // 65
            id("412993069e8e992c-1529fe95a1c31b9f-1fcd40e2743e8b5c-09f65d27c9d0a780"),  // 66
            id("8e3c90e29c871e38-9e160497ddbd8f94-b193afcc738861ce-158cd2d79517a2d3"),  // 67
            id("b76db7e22ecabe36-ee5dc8575533852e-cd4a892ee404d727-4cb23e2fdbdfa89f"),  // 68
            id("12d1b4da0009da26-0f7243fe6fb37934-f4c9135952e7894d-528b5b852aa34f9a"),  // 69
            id("ade09f4dbc692b63-0e42ce2a50b0df18-c7305676a5311246-4e26a38ecd309717"),  // 70
            id("d10d397e0101e265-38df9c25a8c2bb4d-0f45d35871554ad7-b56a3422a312a42e"),  // 71
            id("304a0ee850627905-fb68bcdfa05f7394-dfa1cbc9a95dbae9-b2af8bd01829e30b"),  // 72
            id("0216df532de385a9-5ec40cd8d463b87c-261d51a74186596d-a03b6af165c5250b"),  // 73
            id("ccac5303c4d832ea-8a9c14debdd54bd9-083310aee287a033-c170c609bd4a580f"),  // 74
            id("68160ad57b7434d5-c374d3145e901fed-e2bf9c8eb1eee525-3dbd8720933d15a7"),  // 75
            id("a035b36951f0e18e-23496f8245d94b28-2576a0345a567bc4-9a898e575227dc93"),  // 76
            id("4dea43960439084a-7b1cfbaf3871f07b-1c74354eb60ff271-06f458b6c845be2f"),  // 77
            id("b0d07968ace7bcf5-5c7b34a23697cbee-6436b835de693b37-1793c16d41b56d04"),  // 78
            id("e3855d5c084546c9-63dfa5645ee9a55b-1d2853669f562c65-adc45efd79ab3e1d"),  // 79
            id("35f3dddddbacae15-3b8a6182cc44bde3-110293d702da9fe8-92dbe84b1f96ef4b"),  // 80
            id("6d1034e7ca1ce430-28b9706a95a8730c-356e9198b945e434-f481a37ec378f223"),  // 81
            id("d81062139adbecad-f7adacbf62b6ce31-2a8e55aaa1cd60df-911983763f79f714"),  // 82
            id("a294ae9006a79a7a-19a8dc36cabe3a4a-42d96abf76faa2ea-c1566d61028cc7d8"),  // 83
            id("358f83f9ef9658ee-7342aab9f3aa48e7-3238489eb7e869c9-8abdd2e52a8a2739"),  // 84
            id("8bb767245ef2c916-3479c39cb4796cff-01522137c1f0dada-fcfc8e9f99d9e80e"),  // 85
            id("0d6a4c37c6082414-d27cecbcdf24addb-4bfcf523ff081f90-9e4e34c370f01911"),  // 86
            id("80ac21f75dc2d6ee-f3b8598dd85e6a8d-c9e95549e094a834-98aab6fb8a7bf795"),  // 87
            id("3c301fb3d47ba4a6-9cf6aeea38bb7d80-a844fdaeb3edafde-7bbd9e4ca9010944"),  // 88
            id("e45a159c773a35e4-9932b74bc5c71496-8f403337d9e470a4-f1a5673b7eb63759"),  // 89
            id("9577c023423dc798-efbe5cd81681a824-8c3a7edaeed37a5f-fd3c493fcd7ef38e"),  // 90
            id("3766eb9b518392b6-278e03e5daabf853-b17019e3b2dc5878-e42b8a4cb141f339"),  // 91
            id("dcf849036a517092-c4f1af614b9716cb-5dbcbfe021e464f4-1ebb24583b8c9fa1"),  // 92
            id("c378d2abf2eea2c0-c49cde732c833d73-449be25e05f26640-b5851f0e38e84671"),  // 93
            id("442cab1c6b675bf1-e0b281ef006d27f9-65be99f979cc2e71-3ed0b48d255705cb"),  // 94
            id("b6ddb450e910ae29-941421ce416dc1c0-acc0b4300af2a304-76c1e5716ceda44d"),  // 95
            id("c8637919f141e1f2-24ffcfd992c09193-d471efd49d62ea92-f207304ebdf35816"),  // 96
            id("b5f60ae22efd5bb3-8bdec929b5d6e6c1-3e417316edcad872-68dfd6fe393e103d"),  // 97
            id("20210eb6b760517a-09f1ed603e588550-b46340eddc45d6da-a1a7f994300e2929"),  // 98
            id("4fc95658eff3cc3a-23962f17d203f204-bcf2bd027075379c-0657240b57781193"),  // 99
            id("1d648fc3d67ea206-7e21a6e37eff2d64-0f88c8a2e677f22d-4a4d25cce7084785"),  // 100
            id("c0b56360d4ff375c-2cad3ec5063cbff5-8bed5c97915925a0-f620f0a91425c44a"),  // 101
            id("644c64b762f6aeb9-e29d6e790ed7f811-ddc85d1de02c043b-56b88e3639c479eb"),  // 102
            id("20e19dc13adcfee0-de01b3a1be56950c-ffe129a8c5512938-b3c88e4b24e51742"),  // 103
            id("23ce701b3e8ecbe6-c50862740ad597b6-57a89ae041fce1b4-a3fda20d91dc375c"),  // 104
            id("27c2462e4dc89a34-8c36b6712e7ba058-f5d2745d2dc5d402-ea8eae2e6b5af4a9"),  // 105
            id("266e468f00cd332c-ab8fc9619ef73fb3-b75508ae7247d07e-0ec42555108a946f"),  // 106
            id("5b3acfea65d23551-d1454636a0621880-f2e1ad21b2cf9e4e-40d3c2338b89cf84"),  // 107
            id("86fd2d1d2a9cde18-17990694e1728386-6438adcd97b2b62e-4e89cbb93961a2f2"),  // 108
            id("345420f876aa987a-37aebd17b1d6334d-de613d1a203d8591-4ae0928e48b0bdb5"),  // 109
            id("97920025d1306c7c-28882cd87f9a5a9e-92815b196186ae9e-d5b8175faf484c31"),  // 110
            id("6922e4c7603aad61-c5e0026d1dad1e27-fb3478c24befa9a7-95e18aa551127094"),  // 111
            id("e3970e8fedc4dba0-2c34010b271f85be-93201f255469c788-0f11af338b49499b"),  // 112
            id("49e27430d79d133f-4fd23e416c555582-e8d0125fae377fcf-f676793c98a119ff"),  // 113
            id("4bfcfcbb56dd1b69-43262f84d3a9e4e2-4891fcc2be208499-4cfc69648b1921fa"),  // 114
            id("f08524653e1431a7-99769283bf3ac68f-c77b0374bc9cbe00-a34e7e632177671c"),  // 115
            id("2a7a1814bed7fd15-436628aab918085a-6643d12561429dec-e7c868aadf64dc43"),  // 116
            id("8bcc9e94ea73ce4d-0919c021335e0a1e-4b7451edc880782a-dc56ac719d610bbd"),  // 117
            id("781592ac47c0bb45-655d41bc9e39e68b-05d9bdb07956e56b-165d4e2ea278dd97"),  // 118
            id("1c3e87dbd0f7409a-40cc434434849e4f-370a1f44fe9541e0-f983f8dde7dc97e8"),  // 119
            id("8ffc0d485bda4ded-71c0499f787b566c-8ea9657a173a2b29-31e79a5400804dda"),  // 120
            id("618b4f23d297a0d7-6ece8be25135d479-b1e19b3e615c8bad-e9ae59b9fc06db17"),  // 121
            id("3b66d0a03354ffdd-439382eb8553993f-6ebe76e689b73f5e-f0879df747ced4f4"),  // 122
            id("5a694ae2a56e37ca-2cc6b2e4d7f17222-74996cd83b7af1e5-c12d2382979932b6"),  // 123
            id("453d5726b177028b-61d6745ee4b1dbab-760ab4c74756ab37-b130f8e14fd7ca65"),  // 124
            id("3ea63243ca1c6ea4-427ccd84f3982369-1d6972641b55d877-aed41caef009d708"),  // 125
            id("21591120e19d1aec-1666c8fb9d7c3cd4-697dbbbe4fb7080a-776b9ee175f5aa24"),  // 126
            id("fd17a0c5c8573cc4-af4874ebf2279847-28b5d88d83d4289e-413b70ef4ace8ab6"),  // 127
            id("6d3bca59d5c79a5d-0d75366c5891be74-4ea4395371c2ab91-e09796874c8d5fbe"),  // 128
            id("afcaf6c89c465395-97c6428044c52a34-5b0961753c85c928-2fc537d51648186f"),  // 129
            id("5a5e04c759d5f922-407a1806af8a0a87-408ced0738104819-27092f8637466c08"),  // 130
            id("529ecb78e4b11777-5b3f4645b0aa6fa3-78755325e6714abc-0b3a18a38229f8af"),  // 131
            id("5ab309609b08e15a-c7ed7d8f0f4aafc3-23b6e4663fd708aa-c88dec212c1af94d"),  // 132
            id("15f032f48724bded-ee848edad2368973-b00b666d28dda4f5-b5b4db25b557247c"),  // 133
            id("99958e4a01569fa8-ce69646365598ea5-d24cb6ac22d14bbc-322d8c8c9270704d"),  // 134
            id("6d980e0ce6e325b0-bc58f9c95318e23c-68b7eb05e16008f8-1d5d94717b445e31"),  // 135
            id("0c9aff92067d2fa0-9d283e3f90da5e29-e4dd00b77fcae593-c47e1cc85be731cb"),  // 136
            id("1d9afb7353b69899-1a485442527d4674-52fafe601cacf52b-e61ab785252073ca"),  // 137
            id("1047eddee104f39f-b8cb60dd733fe9ec-ba946791cfd168f0-8e2d1cb2e12c8cf4"),  // 138
            id("3431c7235b4ba989-9c6f232ecb1a6aa8-4523ad052f5d5be2-a417576626f1bc40"),  // 139
            id("7816f7d032471e7b-653b9cd0261f02b5-2ca6a97ea6bf9543-f44cb82912572bd6"),  // 140
            id("a4d0238d89f193b9-f67371738340e27b-b38da5b87307e2bf-4dafedb693db6369"),  // 141
            id("310cd06a143c47be-6b1c7a996c70d0ca-999eda838722bbdc-99d4141fc3f6158b"),  // 142
            id("91fbc53aeb1019f6-d3863c4e5a7b8930-e1b893a97fd2ea72-db12f68497e78f9d"),  // 143
            id("559cc68be61bf70b-2978911c467807f7-6430919cc0cc222f-a4b6b49c7f8eb308"),  // 144
            id("c922ec31d38de116-72254c974b4dafff-1ca8225cb20e85ac-35a70d812682517c"),  // 145
            id("8a3b398e14154b48-e31f0cc4e4f0776d-254fe9eb8ed9de05-2412b22629b827df"),  // 146
            id("6cfa3d9ff159f922-4bba9668563162f5-74b051dd676c9210-69ed505be42b7f6b"),  // 147
            id("4d5340525aa86e40-909dbc359958e932-e22fb70824f8bc6c-349d1a652881afee"),  // 148
            id("e341f1b86aeaf3a4-354e37ed42582a82-58884a447030c3a0-2c0a360328024c9d"),  // 149
            id("f9a8d56f02787f47-1e1fbbc8bbc3d400-f166a7a4b0a2e906-25e0372a120c1359"),  // 150
            id("7ca28c5cde35ee27-d42b7a3f20bc2cf8-6069bf75a41f64d8-e0b669bfe1484d09"),  // 151
            id("9020ba6e315a16e0-ac17778e6cfab26e-ca229ff4b88735bf-7be3b998ac221be9"),  // 152
            id("34f557e67316e041-9b9c7278b16ca760-e01d4cd3ddd74095-c82e1cf09f01ef26"),  // 153
            id("325194828a826a62-ead357b89f235600-dcfe06b13ba4628d-945acd614ae4e785"),  // 154
            id("f05b85a7c6d692f0-e79199cc17645834-a835504bb28dc1ce-6f1bbc5f1dc36909"),  // 155
            id("a643e071eb7b1f61-b1b8617b0b820ce4-f9fbf5dd103c1e2a-b5b2a36ce0d47f30"),  // 156
            id("1c6e73751d42b584-bed90d8e96a1dd5d-7ae52c12b1a12724-fa46f7c13e8df611"),  // 157
            id("f60233d3eeaee0b8-270b81856d3bebf4-9962c90daa5e03eb-75edd9f7f199ca09"),  // 158
            id("e09111de8fb906ca-e20174f0f514e591-69f10cd97a85d522-f649c6c80d4da095"),  // 159
            id("19838725d18cc0f7-27a44ca02212439b-cc53b34349ced331-3ec28770d36288b9"),  // 160
            id("dc19019f568a6189-67461af0ed836062-13959bfdd5d4de64-e5496dcc324dba7d"),  // 161
            id("976bcecd97e5e46d-54a1619f6000b73a-3fa7b95198d5cada-23ba37f219adbf5e"),  // 162
            id("f0be701833fbfb26-184564a9b748d3f0-ab0a637d94e45703-738bdc7eb5931500"),  // 163
            id("627d397b2657bd34-4abe69af0e6a66a1-6f5172539dbe36e1-869e379668f145fb"),  // 164
            id("a140277c2a5754e4-3174b6fa5226a332-1004279b157226e9-e46c804810dca7e9"),  // 165
            id("23a2beb2ac0ccb07-36cf0ccb4696d683-5f657516b0b4b303-3714968c850445a5"),  // 166
            id("81382e93217eb575-d30d4a53d1beebe6-73174a46f7bd0e26-5be2feb8baf6943a"),  // 167
            id("cfd5d49552fdd8a0-a2d1959d8083e611-a21563c49e810519-7d3b4894298b1981"),  // 168
            id("667b26bc2d25262b-6badb5a13fffa33f-4e7c723a9da5be73-cb1862ee5321d1b2"),  // 169
            id("81b37c41819f9a34-da5843d30812b694-bf426d09f80fd298-827e76ec1bee29fb"),  // 170
            id("4a515d372b9cf997-df261d17d8096bee-289a37eb5b156b22-5ca593612d507502"),  // 171
            id("8cd3673d8d489e76-9feafa72becb796f-8628f99ae5204ef7-45309d6c037106f2"),  // 172
            id("35aca292c35829ce-ff4e0f9ba5b7d7a5-c4205c28ecb9b057-412487674ba76f76"),  // 173
            id("20f4175cc80d8187-cb26610551813783-b51d6a56b77725b1-036ee3c02565a231"),  // 174
            id("c86ddfdf1885a961-2e2e97cd0bb5d8a9-9c64dd413bad612b-94a628d924f12e71"),  // 175
            id("0c4ba4c6a2f79eb5-b6db0a0a916643b8-482165eef400f5c2-d99fc46a718fd0f7"),  // 176
            id("3cbd7711620cc884-7ee734be68133da1-8b9afd4bdf934325-94412a8902bad9d1"),  // 177
            id("de141fbba3489b48-fbd17c669bbd0b2e-540d467044051a2a-47ff3b38782df63d"),  // 178
            id("1ae18241ce8133dd-e7c53e62a3987b1f-e4cea6283a8a3a0c-f82e14957bd2affd"),  // 179
            id("b212946374479af2-072402b57676df52-9b066edc9b9c516e-54554dce89222d42"),  // 180
            id("380fa6a0303d06ec-bfcc9070d7a3459f-73d0caad2629ad8c-61216da1cda0ca0f"),  // 181
            id("c0168e512c7b7aff-f53803e14c8faff2-5b2c5e435ad145a7-39bdf8cab285a0e2"),  // 182
            id("8c2b561c53c52bf7-4047d92dd34bb9ee-baef13585a00bd6f-bed604647f59e6c3"),  // 183
            id("d999e28548c5ca92-27fa8917dcd29bb1-cbac2c6fe314ce9d-0001ae36524d5f86"),  // 184
            id("c18e244645c081fb-66c257ffb64be889-3c671753304b3c84-f5e145b9ea1ce6b3"),  // 185
            id("aeedcfdc4f6e9e84-c717a638da049b44-6c486cf0ae640d07-35619c1427686c41"),  // 186
            id("99be2bd11d438259-24595495a76c5b88-d19a1033059378c0-b6047c1e5eff0fcb"),  // 187
            id("2fddb5e2f0b8ecd5-e600e69718d08ccc-0790e61852d6b54f-6c2118d8dae6c73e"),  // 188
            id("9fc70c797e19a1f0-8d62e6b005d7dc6d-b7d4a58ea796b5a6-95462b331ce1a32f"),  // 189
            id("cc4452c021bbd764-f9e4e38d3c502cca-583e241da9fa885d-ae41e616691ff6b2"),  // 190
            id("a1b0aadf92c78f85-6abfc05a2792b620-64b1a2ad1d272b4b-bb24f93c0a2d5c39"),  // 191
            id("00ccc0070c1470db-d5e42b1c6c3125e7-db0a7a8271bea7d1-83fbc305fc14751f"),  // 192
            id("0466addc13e6fb6f-1363febb3d4bfcfd-7a80f5e03db29ffd-c3267725f7b5d722"),  // 193
            id("15716fdf82c97b8d-43205ce40d35bcde-9a427effd9f93c12-81fd9ab950302b62"),  // 194
            id("85eb7ed6482b767e-8f1e99ee63c78b59-d6d28dde07f8ea67-be7b3d3f9744aef7"),  // 195
            id("fc34c61b0adf6701-66ad9c02fc586877-d4a7f6b1aaf9974f-5c901ad516eb415d"),  // 196
            id("8ae058cd8585b1fb-14288754fd85dc7b-f37471aa9d29ceb6-69cc48a1a2854ca6"),  // 197
            id("c85e53b6f012642d-162e15c034c19cc4-f408e5ee30b4ec6a-2521041c65e67a67"),  // 198
            id("e31a299df9bc71b0-aeb2a1914bd4a700-135536eaa57b1223-a5bb85521db8dae4"),  // 199
            id("9fb3fc0ab81884c7-811655fa591e56c2-051720dd08bba3ea-db82607c9a9f4c8d"),  // 200
            id("c139fb965b7bb079-0cced65f75d3a20e-cec155260607bac2-f8481a87a3e90472"),  // 201
            id("cda8fd420fe77cde-8a16349a9e5da6db-8dc1590b1a10071c-26ea4f596facf69c"),  // 202
            id("a13c596b6fe3bb62-6fa58d5c6fcf5bc6-06209df0c1a186df-9e8cbbb228449d37"),  // 203
            id("fa878a4e7594f643-20c43146e678d72c-b60ad9ef61f43936-9ef5919cc90090d7"),  // 204
            id("40ebfe071dc669d5-bbc25ae3c6edd60f-7c848349445357d1-a1546e0a373a056a"),  // 205
            id("3947839e88e14187-809771a91b58f78d-8201564a90e40337-d9ffbdfaf4939621"),  // 206
            id("625ebc40ac377552-a3247f52e60cc62e-9e1775230f278568-abc2b1e0aba311a3"),  // 207
            id("a1f90bac46b5a379-a831437214916906-c90321d96c6536a7-d1ba605321c76212"),  // 208
            id("44b1235f34f8ebd1-40fe4b2e9dc22550-ed5df163b516deb3-103da2fc7f0ad5f5"),  // 209
            id("6d5459cdedfe1564-b939a2c6e774cef4-89fae39124d23bfd-ed0df99f5fbaaf7a"),  // 210
            id("8806f8af323795ad-e7f9ba8f3d064657-bc0c424ce58c8caf-f22a5a2f2dc9b26c"),  // 211
            id("7cd8dd7a82eaea30-afbf73c438bec7a6-8911ad3fecc4a421-474376c9dc97b352"),  // 212
            id("2b1cd31ffc0a05d5-0e61f545a0f3c710-c5aee72e28cfd0de-c2536d028fc128f0"),  // 213
            id("fd65d3bd64221763-4a4f0f5d15e010cd-a542cb3e115a19ef-a9a79087f6d9885b"),  // 214
            id("068f929d45d74058-cdce3544f9f788a4-d83983ffe4ff268e-8a266d74eb4cbb7c"),  // 215
            id("e7b6f788cdb8d99f-a16bfdfed7ebb71a-91535a5a051558ca-9a9aba10d722c694"),  // 216
            id("7f413d25273ed6f8-e086854fdb705607-e9d0d392273b1ee7-80807db070f2e6c2"),  // 217
            id("abb95c514f5ab778-db9789a7a8b070b2-ee0ff394bfb92c3e-8d07bb0e705eed1d"),  // 218
            id("c4e871e6dc3bab12-c0e603eaa18cf432-f205dfc2cecafc70-5ecc94cb0c88f19a"),  // 219
            id("cb6ad80c8d7ce442-e79de186d79efe39-604c4d86fb0a9578-09e356a98a7a67a0"),  // 220
            id("0fb37bec06cf4783-9ab6fcb5daed2e82-62eb65f478418f59-a7b722da77864d65"),  // 221
            id("4073e3eb3a8597f9-38a5f15323d5396b-a1e3176bf7fb59f7-83e5006b2de7a521"),  // 222
            id("87ca61c02dcc468a-fcefa55e4c21be9d-9627f436b1098ce6-f95d71844d6c1106"),  // 223
            id("b37d14ed73c24882-a7f93adafa44e5aa-a321f9edb3a34c9b-b44829c3f408bf94"),  // 224
            id("1b296fb4aaaa1648-1219c731c7425a23-7a779653a460d6b4-63e877eaea70d035"),  // 225
            id("8a1704e3eaa7c7a1-7e84d2505fc43092-12d23587ef20c273-c344570d81cafb1a"),  // 226
            id("fd69ccd0e77ab85a-9412ae8f2d4b555a-dc48e90549c7c424-b4a8afdeb01b8089"),  // 227
            id("0619da0be3f92c88-e19dd8deac1390bf-de4066207758eb36-7e378a27cb328274"),  // 228
            id("f095374f94739dbd-685f9f0043283914-0cc1d87125cdf105-aef7ba9ded4a437a"),  // 229
            id("eb69365c15d7af8f-4c523f23a629c902-f1f8f442ca377fe1-d329464e803cc527"),  // 230
            id("ee75af421a6fd79d-cb77287dbe46a78d-7ae85b3f6289df74-7220ee6efb2a0868"),  // 231
            id("db7a6d5fbe0d94ca-3c010bc595ad13c0-17e35c7bf36dfd60-7fe73ca622658476"),  // 232
            id("74b54d8444ccaa3c-cc6c7f6282a48491-b5e61f2e920d4a24-cc6d56e9d4719b74"),  // 233
            id("ad6eea530663188f-ebb5f2de93931d2f-d73ba4bc58976da9-cad3ea0436dff71e"),  // 234
            id("c1eabbc948361091-686639edfe331a9d-18bf6595ee9a2a7a-ea22c30836d410a2"),  // 235
            id("58a0c8a0ce62fbfc-cd92a0d6b3512010-c24bee642ab927c5-b83d62f94f7d0adf"),  // 236
            id("e88ac21510f0a351-f94d3afc44070853-bcad61bb985f5dd1-1e6ebb21d4d6ad6d"),  // 237
            id("c6739fe56121d916-3f5662b776df6550-10b4bfc9586ce438-a8e450c4ef508fce"),  // 238
            id("4be556e84ee137a2-84a2b300d1bbb023-ea9aeb62eec9a149-2101943386797138"),  // 239
            id("dd5a4a12d1566eb9-d58eb44d1a48aecf-6a042e6e9e6ba263-d80860695c9e9389"),  // 240
            id("9f3aad99a5e6db0c-9459ff70a3b3f60a-35faede447022dcb-61145d45a222bf07"),  // 241
            id("15683ef5ce0d7352-eb8dab4a40b277de-d1ba8271cca605f3-a1e9e852d5c17d92"),  // 242
            id("49d703506c0e3399-5f695d0fddcee202-6d3412e5ab26f336-d6a4a5506a0ae501"),  // 243
            id("c2aaa98561db2e98-3cdf730a10418f7a-76408bd618db9006-f070448006f5adca"),  // 244
            id("c544180d6849b946-dea77b84cc6a568e-c4c4d80c1c8e0211-cafb5d7272be0287"),  // 245
            id("5edcadeee8c32a49-681bfd362bf3db44-7c562a3bb9f0772f-822300aad702bf05"),  // 246
            id("5e81c7df08c084e7-72680e31cd5b533b-734a718352ac1ab8-50e0600c262ac43e"),  // 247
            id("d252f39ebb8191cf-f46acee153b026ed-2d572bfd47a86fd1-20877aac4331ac2f"),  // 248
            id("908f92ef34b78e23-a1ce8886ac9398c3-3f019693b6b27ad8-300b1288751dad17"),  // 249
            id("ad6244a67a3900c6-1e2136e9c1c8fb03-84ea407e93294969-439047828729e486"),  // 250
            id("60efec6e8899b4b7-7d3623fe3b761fb8-b4bfd65692f67efb-2e76910460704e82"),  // 251
            id("03e580462de2a2f2-7e3331a12d8162f0-81b10f58f2a55378-d891acaca840166e"),  // 252
            id("293a2898c6193120-0307ab23447d9865-d5a3f2926b78a14a-616df934a8837f73"),  // 253
            id("5704052b8037f149-12daf44a059edeb4-5f246a0014dbcf68-c46f821c4e0c0cae"),  // 254
            id("9daf887ae3d98865-8f2c3e2100270a92-0cfef589d06c0417-6c986230a0ea9550"),  // 255
            id("c14a5903f86d7c93-4c6764c33f60cea1-08e62fa2db4f676d-1ee38c99302d6e39"),  // 256
            id("38c59d6335594c54-b728a4deba2de67f-219de70e249c2892-d4efa6a18ee7cf29"),  // 257
            id("7f45a6040f44b348-deab33c56f1f8f25-38bbecda452398a1-53bdc77e15b1731f"),  // 258
            id("4b9fc0e52349d2ae-cbf84fa38155e45d-ac9fe2fff960c328-1e8c21a93d4d37c4"),  // 259
            id("d077e2c6a48f3e1f-a7cd8fbd2e1b5ffe-cd452110ec0abab1-7090d3538daafb11"),  // 260
            id("9392b6a359e64d4f-6fa56f598b906d7a-37af55f67b463726-ff523e2891358fcd"),  // 261
            id("6e3514df7a2070b0-bfcb85a5526015cd-cc75896262e4db58-a97663f57885ff1b"),  // 262
            id("fcc3a8d6effb372d-16fa83ecfe4d583c-7a7aa0e858ec4968-c5a06aed480a58b5"),  // 263
            id("53de917e18a9cf86-a0f5fc2d23e5387f-4dbc7f9aa9b66b2a-fe5b8d1715ce480d"),  // 264
            id("ade70655b013ba39-c70399c47d8a5295-ef1ba299002b9a20-4f3ca6eec86b6c5f"),  // 265
            id("e84bfa7b22f3af59-79f60d351d4eda56-f576a90cc29d4849-2565541e36cb4ce4"),  // 266
            id("87ff505082e46875-dcb0ebbf51120c2f-4324d2b4cd6ea5a2-39a9ef6855f1c343"),  // 267
            id("465be95b24b15c70-0da50be00c93c05d-d0afa9280598c65e-98cf8c0b51d6da78"),  // 268
            id("613a687443617a8b-6718673f65a53512-c09f4cbf9a7201be-6113cdf7c9a888b5"),  // 269
            id("a690e9bc883a5c40-dddfd83391850cfa-8d8a3ce17a391edb-bf33ac5eb4948978"),  // 270
            id("ca06bb70af4720b4-a4e274ab6d38d0db-6914aa60f4ef822e-fa9c5cbd09ca6cca"),  // 271
            id("2d170427232d0a03-10de7c23cd1fccc0-05dae4b8c84285d1-eae746351ca30087"),  // 272
            id("41c7d34c9e3837f3-69205d4b21e55da5-4f369dfb28d47622-6dbb642c13fde291"),  // 273
            id("66a32c02c1426fe8-3db609acff2e5de8-79dd6b095f44bd8e-f9dc70b0807b807f"),  // 274
            id("1547e7b38287c1ab-e65eda8a9ecb726c-72f581672b564bd2-83743355ec89325e"),  // 275
            id("2de5d2494801f574-9c2c8ff96ba94836-3f24a9766b403ec2-6eeb684791f72437"),  // 276
            id("3b4d2717ed4099ba-ed3fba4d56858ce5-6e8fa437bcfd7227-68a4e0aabb409a3c"),  // 277
            id("4efa0b7dbc26124b-5ce22bd3ebb98931-a4b03af927d0a31b-acc71859f23ca535"),  // 278
            id("1c4bac5761bb2d6b-ab8f259502ac3e58-c07bc7343ea84218-d0db592aa22e14a7"),  // 279
            id("12e1cd05a1d25c13-c77ece0be48c00ed-c086a3ecc87c66d7-f0b7adc74a9c1a11"),  // 280
            id("bf057628222ecf36-b4368d66700ba892-0cdcedbd8e334fb6-31b45b66e9d024a0"),  // 281
            id("03717ad8dcbd0991-be97418675413011-fb5e0fe1165d7a53-ec374684009d28b6"),  // 282
            id("e648ddfa94a7894c-afcbb11fe7e78154-0874271419379d71-f88f33f4115b2ff6"),  // 283
            id("5d7c5eb9e2b54591-9fa179d956ddfe0b-dd42187fe63545fd-9843de139853ce83"),  // 284
            id("4bc967367408bd38-e9121bca07dec530-2938f6f9080ed76b-5834eb397acd0ab8"),  // 285
            id("473d5279736bfafd-478cb51d7ec08f7a-145daf44e57ec623-1166c3c869b61a27"),  // 286
            id("7c2b37133f1e8317-ed0f8a3aefa9baff-7cfaea3b3bc10508-0dedb88150755c0f"),  // 287
            id("c53801fcee4feae7-a35ce5290d2807a1-540bb99ab591de11-16bb62de5cc5a990"),  // 288
            id("487c6ff0e12826de-44db6757fb764e8b-ff58cdc779ea43d3-d7e88f53a313a64b"),  // 289
            id("fa61a6497d4e274c-6f21adde7894d959-7ba5c6f1769e5ff0-40450823a79d3fb9"),  // 290
            id("815c7bbe469f5370-5e92daf387d672ab-24a318007648a101-0f30266285d93a14"),  // 291
            id("a4d8f984d39d00c7-e71f2c676931aa43-274ed88e0ba2bdef-129a57cbc6131024"),  // 292
            id("1ce6f2cc097d64c8-7e9d9e492de603d2-0554a9e01395ab34-0bf7a005fa48ade7"),  // 293
            id("491fa352c5b901a8-a2a43d81e89b9474-ef452d45b961754e-059ac494d91f1e96"),  // 294
            id("e08a86b4491a160a-34b5d8287f32fac6-dd68147b8f7ec2ce-13e701d711cdf6ec"),  // 295
            id("01842e926efad123-96e22ea8bddd8e50-da183c35ebf91d6d-cb9bf973a2624705"),  // 296
            id("842b49cebd2ac5fb-cb779cbcb597b8bb-d12d7915399afa9e-d440cc22b8ac8bbb"),  // 297
            id("cc142fb2b51b36a4-0cda7ab4ad730fdc-916f1921a7cc9ede-30b56b68cd554353"),  // 298
            id("f172837b1bf9ad4c-bb1b33e37811bb6c-c90fb2021155842e-e926a29752d295ee"),  // 299
            id("ac310e1acf7593c9-c1c6d2a7b0437fa2-5875053d98d8cd80-199cfb22374e16e7"),  // 300
            id("f621b56c389e6a86-fed2f4b0f775da7a-ed52bdcf51915d52-7652811105fc819e"),  // 301
            id("c3910f552cafa861-82348bc388526742-70f84eca21233b2d-766d5619091805a1"),  // 302
            id("d0a5a8f2cf3bd967-bb115e7ce3848427-2e41c9a23ceec11a-a19d956d44cc4e74"),  // 303
            id("b65ac708672ddecc-c35002ea7fc35f68-ddcecd2ee47f39c5-b723a009a9680b84"),  // 304
            id("1ded9477a426406b-ddab3e78632b6fe8-df967bfbb4e565f9-9f5119746ca716c8"),  // 305
            id("81e301e1f92fb405-c67e21a6b3dea310-de03463cb3da79d4-d0799f9aa5cacb2c"),  // 306
            id("6f57f0e3f6ef641b-a6ba62fd558dcbfc-a1331e94607e371a-87477ddeb0ea4740"),  // 307
            id("377ebf55a3f86940-7ec62971e07ef2cc-9d9c616fff9b5f61-d3ed3ff6e5411d2f"),  // 308
            id("ca88f5200b587328-9f94e87397663240-7823c3430c623050-53bae28777b675af"),  // 309
            id("1db6b65527db58d1-95b4a15ed1df1222-b7a72e1c7f1fd81e-912e54b8a30bdb9b"),  // 310
            id("43faa64f491ea996-44e8221a14955667-38b8913b5d395155-71b6a201ede26ce3"),  // 311
            id("7b8303aebbdc1e62-a318bb6e77b71a5c-09a0f8e2a7a7b3a1-86dc4ab6417d006a"),  // 312
            id("3d582a18218b88c9-c2e44acaf56f3e99-18e97fcf03abdc3c-57fcf2b000e0b291"),  // 313
            id("e040df529912c20e-929f3c25eca7db65-616c7fe7272ac3ba-05928dec293cdae2"),  // 314
            id("4acee42f06493b69-6fd1243a7ec458cd-7af4dd1924d512ca-f3b89a3dc56a905f"),  // 315
            id("1ad90019fc1704f0-11e626632373b263-8b409097d01c86cb-d51d521148de17b4"),  // 316
            id("b4f53b10a36928b4-fb8dcf14d865af7b-94db3c4f022b161c-f4d4ae17b6a555d4"),  // 317
            id("10991db39424ef7d-c1bc7a121b15531b-dc708b9104b8220e-bb3f782512908806"),  // 318
            id("6c3d64ba0c653b76-fc9e50528c6bbef9-909da85f2041a93b-303d1c2f7add3e09"),  // 319
            id("085552ae75efbc9c-619b963d25f72564-c018de2f12cc987b-b382d8cc775f81e7"),  // 320
            id("cabff03f06020402-bf2f8cf0906c440b-ed8be7c225111417-e54c75ccce2271a3"),  // 321
            id("fe33233f3e9b5d24-b5998234b31241ac-20578534d9d7e5d3-28dffe7e6398e91d"),  // 322
            id("dc3482add5b5e4b3-afb9579f90232ba0-f588b0c0e9f134ef-60d201aa3273d6cc"),  // 323
            id("42d0e126bfae4cae-456c8b2d1b72bc87-14ade40a54c514f7-96883871d19c6f42"),  // 324
            id("6fd1870d2030ae9e-c89766169a4cde19-5f61cee1364376d9-64b09309a8b6aabc"),  // 325
            id("0345ee046ad16053-3b3c3385a91943b7-2555c268f0d49c0a-5553f96f85d13b17"),  // 326
            id("3a02e1cb1ccaf574-f7c1b8630093588b-e6e0fdf700822626-76f497c75d34ada4"),  // 327
            id("23ae8fecb6e303b2-9c968fed3f2fb684-11afae63d3a34c13-385b40ae93c59fc7"),  // 328
            id("3d75fc9f1e799455-bfa47652e85e1129-af6913ba388e3901-ff8b8be2cb39295d"),  // 329
            id("3f69527ded9816b5-de22b55d71157802-407db048e2de4d1a-35b082a301a0f308"),  // 330
            id("7547b51303eabdb2-de09d2815df33628-5959ccf1b0301e72-eb51af14ca478f2a"),  // 331
            id("a1efc943c3b3edc5-33d449a5266fe8f2-e6190e85b65a4a05-47abf37b344790ec"),  // 332
            id("8851d78211ac2aa0-6b233cddfe58fb4c-fd56e6c715fdb463-dc8c815594f712a0"),  // 333
            id("8eb98b093b6190e7-fc0ea1385d0014c1-bdcdea6da60d9d7c-200b865748d41c6d"),  // 334
            id("2e670465387239f3-051fdae88c7a0c17-54805186a0a6d681-f213e0257fdb5e3c"),  // 335
            id("49902684ab545af5-e88b9f17a41cc1f1-79918e8b61282c79-a34f18ad96ee5f32"),  // 336
            id("17134d3dd7cb5d86-5aaa3517782b1b1f-98ce3e01b43e5d34-3dbd8a9b4064918e"),  // 337
            id("09d73c6ccf193c53-ed89c7a1e16e39e9-9b49a5735cd38cfa-35d247b0b2b04a10"),  // 338
            id("25ea42db20de9f4d-e890ab048b5379b3-9c9f6569e2a89bab-04d1de06c3dbcb07"),  // 339
            id("06f04765746b6b14-2941a8273a8884ad-0e3992da5f3ebcce-9ae99e56706bc3f8"),  // 340
            id("59bb814b41593056-d89f8a408a7a931b-b06ac9260c238b1b-f27520ec9a652f7a"),  // 341
            id("06c66f6e5fe14d02-87267c7685c1f4b6-a274ead4e646cc0a-77a7a367701ff331"),  // 342
            id("2322583b400fb2df-41bbbe0684a50ab1-8f447c73ac92ed33-f529c6ff92f488da"),  // 343
            id("d6eea4977ea1c172-7bbe33f341644ad9-fb6f792d4a67b0d8-a279248395b674b6"),  // 344
            id("92db0408cf2707f6-acc4009bc43f8ce4-b9fa929fb608ac44-7adb07241786eb3a"),  // 345
            id("576f1818a514e21c-7a4e4a1d3e5f8a2c-35cdd86985825b0c-b76fdb6d3a7c166f"),  // 346
            id("6b239018237ce0ff-72ceb16fd4353d4e-8bd98a1e534cd0f5-67316f7b2c6c3bb4"),  // 347
            id("31bb544f43f3b214-cee361a60ecb17e9-4f763b7f22d67b5b-100ff692ddb7f8fd"),  // 348
            id("e718b8314b4f7bc1-9ba7368766311053-ea37274843adaed2-28a7b8e42e135fec"),  // 349
            id("594b04c059d57a99-81c9f073ce27eed1-0e232a37cd13597e-30d965d2752837b7"),  // 350
            id("1a4a8e7f3b91ffe8-149d79433bd95e39-4ecb1221f522b980-7447e99fa91a8719"),  // 351
            id("a96dc82b320417c1-821273b2599d1dfd-4e89cfe77b7f374a-e9f3042718f60e6f"),  // 352
            id("ee6f7739c2f59694-bad942a70de9c61c-a1ebf374fc096af8-0806a8a93be1132b"),  // 353
            id("b4553a541a64ee56-e943aef72265e65f-bf5ef114b00b472e-51b2897cb44ab8b1"),  // 354
            id("bbaa1d0efca20bad-6061424ffaf3a1eb-5c24189fab78b3fa-dfd248a121283b12"),  // 355
            id("c92d166510003d1d-c45c32d57137bb8b-843a9755ab05cda6-f849e3716ecc9373"),  // 356
            id("a4f4372005e4df7a-d01cad4e4c2da5c1-ad3df3502640bbb1-ce97b890591ad139"),  // 357
            id("b33e84e05d7f9797-b678e5699f8bf2ca-c2db29f16943a136-8b21ffc8e1ee8eb6"),  // 358
            id("32998b5135c5377b-5282df113edd6547-59aaecfb1af0a67c-3f80b8d143998fd4"),  // 359
            id("8c3a50378e25b721-3675d4f8b59dba59-080f4efabd193657-e1aadf2c1d474fd0"),  // 360
            id("1e61b1d28b14407f-3411db53cf1c47c0-fe10cb1040ac8ff8-ddef714635fa5138"),  // 361
            id("5d96fcf2a91e96a4-c1b478797067d5f2-2c598b0b52d1d16d-0836cf49a6530f60"),  // 362
            id("6dede8c1803c1466-0faf03563580afb5-3878edfb2efe72b7-f976379222e23261"),  // 363
            id("d86446e22ce9abf7-38d26055c039b838-a8882ab26246b373-499f7031191ce9e0"),  // 364
            id("068b7664c8a85c23-35132f15c21abb73-3c7ea4c73d78cc78-ed251606bfb0bd26"),  // 365
            id("c2c4ee428919cc30-f4292293a84410b2-1b3cfb1f88201d41-82f1e75ba9d03007"),  // 366
            id("3321e7c8548dcfdd-7ef6b65f8ecd3ba9-f58649b940520bec-5f7190f6ae859037"),  // 367
            id("e013a47eff7c644f-f84bf04d18363ecb-225314522ceecd2b-2942d055d446b05f"),  // 368
            id("43c1bf3c91a66a7e-a073e594c59261c1-2ab9d4350b8ba3c6-832e2c9f66fe4d1b"),  // 369
            id("b03e3835e14b9e6f-d48340b9164fdc10-92db0cea720ab75c-b6d6109c033931c4"),  // 370
            id("0fcab6123463713e-ffeacdb2977baffc-d3a95dc2b5bc670d-63e316952f250140"),  // 371
            id("5190e7386ec94dc0-75c1e922815446c7-3364b70a6e36a777-f4efb113310212f5"),  // 372
            id("dcf2997a94aac4c1-bbf42d4861af9cd7-8de217a0461568a9-51d4af5a1fa7653f"),  // 373
            id("5fdd0803e31d1223-581ca01ecc1a95d9-8784ecedd30a2332-11f7c6ee4c1b2137"),  // 374
            id("f716155de241ea4d-4a1dd939968352bd-4b6aa1e3fd0f74f2-8cc5bfef6dbb3d55"),  // 375
            id("d1dbaefe3ad97cf3-fea075e3681d8afa-5b6493b8488eb3de-28479945cbe6b729"),  // 376
            id("08bb7750380fe9e9-a9ec14fbda9b5d4a-8d993d2e6357a26c-74b460b5ea94e97a"),  // 377
            id("5aec09fc2b4e1703-e12cb160a21b7514-a01e4952143c544f-11a4a24255bcbff2"),  // 378
            id("5975a329ac437e97-216a1820de7c86cc-04d46a1cc6439e53-7dfc4d6c3e3db9bd"),  // 379
            id("3bcf324d0c06c0d9-4c3e70b7c8790998-c0b71a047f994938-eda635328129fd6d"),  // 380
            id("0084b8a4b021e0bc-37b5ce7f992736fd-796822f31d029ba0-a75a210a6ddadd91"),  // 381
            id("64c6a1b5203fc137-e141df0c64275724-357b9966e86b39d7-a114b5fc63a2c845"),  // 382
            id("a8c347dc6b6949ef-3ca7eb59e49f9119-72d4b2846860a895-c8d0395aa2fb9c02"),  // 383
            id("4cd2496cb99870a1-0cf59b946074f322-2f40b64089dbb5e0-78e5e147ea738219"),  // 384
            id("1df7ba4596c52c25-870c87c22980e165-61a86fc892a4ba83-e2f592c5ba902701"),  // 385
            id("95d642874d56f999-e91521f8d9bf9570-ae2afc8cefe18704-be96ac7477b93163"),  // 386
            id("b4a37c1563bb593d-9ed5b4625f478ac0-9660fc0203c6ce40-86430ed30c5b7819"),  // 387
            id("7754f342a35b8196-898ea5f971294d27-baa7e906d53d3352-bbf3501b7060099c"),  // 388
            id("9d9432a3c0b9b681-3796e641a19b7f37-aefa411893a8f5d9-83cb5aefe6da7d0d"),  // 389
            id("9a4f4fde6ad7c9a5-f3e0cab7caef9f2d-a9ca1f530f4afad3-dbb5e818ed5ceb12"),  // 390
            id("2c76c74c662d486c-9fec7ecafe486649-8cf4c80bcd650c56-e45e9cdbd48f42a1"),  // 391
            id("bd055802a1140f07-a1e859beecbf5482-babae585f4bef4b2-9f0ad127c81d2b9f"),  // 392
            id("32523d82fd9fe827-92137c2770e54464-65a22d807a4772fa-206bb8bc9a0fde05"),  // 393
            id("48bb89db99d06089-f9be26040cbbd7bd-d2de24b9ff84ac82-2553e28cf6ec7cd6"),  // 394
            id("0059df1f0720dc2b-37aa08cba507a995-e4561519b7397e33-756f59f76f3d0beb"),  // 395
            id("099b4258d539d47d-8bbce72355a561ce-1858d57f761b67d8-40669b9d05a3a93f"),  // 396
            id("c7e9a772eac6ee32-494c82ae188cfd8e-be52c40e3c86b9dc-82b28f2ecddf2e82"),  // 397
            id("f9122e23da4bf5e1-c6bb9892cefcfd65-291fff0810cdf942-6113296e538ab25e"),  // 398
            id("096eca7462d47208-7718319a203ec307-3af2bb511d802bc2-a01ceee2bad80025"),  // 399
            id("0d371e8a70af8dd8-f50c0ce6b660a315-49fee218793b5f7b-6b15061942403f76"),  // 400
            id("4b1846c6c37bd868-91c742b14588ae46-2b784a13f52a6617-f8e5226c7c93584f"),  // 401
            id("5ef27c629eca3fd3-f7efc6009bfb0de3-043929b1ec73b96c-1e743854a5b56f80"),  // 402
            id("0305bf979875aab0-97d25cf8e96156f9-81060ad19c35346e-3bb4fb21c9977910"),  // 403
            id("53a53bd89ef1f2b8-693fe81c502bbbc6-4e5c6e1cf4a7505e-f2b46c6313d79734"),  // 404
            id("b1b3cffc96e5517c-d2f28264ce571025-97a5e54c020f4129-93ce1932909cbf76"),  // 405
            id("ff1f95b45c4511c3-25b2b3c4ea3b84c5-13c8bbadaed37454-3cef4788fe41684a"),  // 406
            id("f68b8e6e8d14580a-9a2d1f2f8c2b92bd-f88680a4f1463cda-4cc0cc105236116d"),  // 407
            id("5b64accc1c41948c-ce250a84bea7338c-cd79602bb45585a4-0a20dbe299e3f78e"),  // 408
            id("1c493ffcfaa4b400-0c4040549668631a-f2ae9c2706547299-26a995d11dd2d6e9"),  // 409
            id("1b5c7dc1d120c9b5-3f6d2b1e0aa7b624-100036604047b0a8-d8ba7843384aa372"),  // 410
            id("ef963e14994e81dc-5972cc35213f2caa-3888b18f6175cdcc-5ce249a444613dc9"),  // 411
            id("9ba557bc8457ad62-ffb75b93b392ea49-4f6513e9719845ed-893196e0f29b0e17"),  // 412
            id("bb49bd12dc11b372-bcbf47eaa44134e8-769fa35d82784e9e-02cf57030d13e693"),  // 413
            id("c7c0890e9df79a69-36aeb570fc19e75e-d76b4bad429b8f3e-d4683c62011cfa45"),  // 414
            id("dbb27a917879d62c-63f6079434134dae-fd364c54ad374a42-000a1dbecc28a929"),  // 415
            id("c18d8eff7fb1bbcf-1fa2a2274fa187c1-9a00a6109fc9a84d-0c3b89dee7482145"),  // 416
            id("348c7a31440138a1-6d77a8f7ba7d02f1-045dfa2e3c14e605-71dad65046cced50"),  // 417
            id("1f5eb1423f313a20-e49cc1c5c44cb42c-777180daf12e608f-6def7207e2a3101a"),  // 418
            id("55a9ee3999ca6d2f-be18a37fabaa7d93-6a016b61b3193c35-3c346847697d2293"),  // 419
            id("d06a3e48acd2a8b7-d3fac8e473b2c1c6-3ae2fe5635bb2ff3-ef9bc014de1ad917"),  // 420
            id("77f43c207297b332-382354318ea2db2f-bebe0843520ab5c7-89b20831f1e84ae6"),  // 421
            id("6dd3cf4af831c244-65512bbc7bbeee1d-0f05fa08428143ef-9afbac2c64bf8831"),  // 422
            id("ba4555b3c0b18e56-1508dcd2f200c99f-bde39b7f09ed1718-3a503a2bb2905db3"),  // 423
            id("d1f7db08f1505bb1-be38a17e1b905995-a5a22da4e1770c53-0c67e4774421a2c5"),  // 424
            id("31150a4101d1b716-639bdc1aee083e15-cad57736761dbb24-abb8dc7e620079a9"),  // 425
            id("9238b0b290f5904f-947fe434bd4f6ffa-a44dc0b55555456e-476aec4447cfab3f"),  // 426
            id("b55941de754b223c-d5c640374bc65635-10afaa4a0124853a-90d7d28bafdbe2c5"),  // 427
            id("22135669045deb7c-1dcc69c15003d214-686607512e1ffce3-437d607d3ae2ba2a"),  // 428
            id("e3212ecd28cc0bbc-440d5969ba2842c7-c3fe34c02c34b20d-f1f1d8910618f64e"),  // 429
            id("cb795bd348e132b8-f01a9e1a66e36129-29e09b7a66d72774-d6d9063eb6f65c19"),  // 430
            id("795963a0372efa72-021d780057f64831-b016c2956ec4a880-3195cc43f6e69c98"),  // 431
            id("741b37d09d0dcf54-24ded8e8cc291205-91f16f9e835fbb95-8945302b9e235104"),  // 432
            id("26207230e69f371a-ff17702ac56eea5c-847cc26e5349e558-3a5e29f435b3fd12"),  // 433
            id("3c33ccd8f12b8989-f464d5242b63d9d5-ecce4993b20e1372-2869861cc4ee601c"),  // 434
            id("061c91489061721a-e83ddaaea8441799-a3c8173a75341830-ae2150d5b2e0ad6b"),  // 435
            id("d5ba6dd0382aadfc-f3fd9d8ea466d8cf-d58f40880bfd31cc-f3bdbffc3de3ffdd"),  // 436
            id("358092cfe1c82be2-1a232d6d880321f0-2cffe3d2d6df855e-69fe235b42be2a70"),  // 437
            id("ec8c684f2ca3fcea-b155b5c7eac57e75-11a8a513d382fe7a-c39bc17a5db815db"),  // 438
            id("df3605d34c9733dc-e34183740e7e77cd-f1835637effe1527-8916c1ad9140ba65"),  // 439
            id("f1d5a502395283e7-b96fc9abfee1a8a5-ea2d672d10c8036f-071d7f5801796b4b"),  // 440
            id("a6970629762421d0-06809a222c3a089d-4ae1a70fd3a78f61-6409d92e0b743ceb"),  // 441
            id("2434fba79b6ad05a-6a72d8e9ae414835-c32c16e8924787ee-042524b9ca445664"),  // 442
            id("7c6e8a09928b64f7-4bfae6c5201b87ef-c5e6a2b5f1fdf644-42b57789de5236fb"),  // 443
            id("d54a668321500d46-aad4b8dcff960bbb-fd1af0a03dca056d-8d4ce190d34d6155"),  // 444
            id("675e78d2a24a2beb-e95c74b637099aa8-9315887240030c54-0d02efd6223d06af"),  // 445
            id("fc9b55627a9cf226-63b95f0c78bf0074-6d3774e4ec946da8-3526b840f39b43ff"),  // 446
            id("3b73e470c0fc5c40-c399bcc51093e5fb-704b89f5a239853e-720257c1245b4193"),  // 447
            id("80277d8619f8f460-4afcd31d88d27009-950fdc5b1e1314b0-3411722383338e68"),  // 448
            id("444fb3837ea20d83-70078308d661ec22-b28824d365ff7db2-64c0730a2ce869e1"),  // 449
            id("24c1e007e8af3839-cf0e6fe8fb65f7b3-8287f632c96612d7-b6f59c540f2e5fd2"),  // 450
            id("b5ab7275e142bf1e-ec3facb16d74fa0b-22a88c9565e5dce6-90fc6723a0423a96"),  // 451
            id("3f052b240fa9ca76-d166fb8b0aebb28d-6b25676c8ad00b73-3036890ff95b64d2"),  // 452
            id("a36f5bafb7e9dbf3-36783d8643998669-ebdc020af0246811-20e287d4b61ae927"),  // 453
            id("424106da2d54d463-e622f60b0a1a88e7-9647c3363c6af4ae-31a838bcc0126190"),  // 454
            id("21cd447139070029-fb4685195ff13f7b-4f3e8f8e0f228455-0b0a867c61e617ad"),  // 455
            id("09f86bba22795915-decffece640ed9f0-2268208f9b1857ee-ceb936dab1eacb05")); // 456

    private static final Set<ByteBuffer> lockedAccounts = ConcurrentHashMap.newKeySet();
    static {
        lockedAccounts.addAll(lockedAccountList);
    }

    public static void main(String[] args) {

        // This script shows the total funds in the locked accounts at the local frozen edge. It then considers
        // funds in the seed, transfer, and cycle accounts to calculate total coins in circulation.
        Block frozenEdge = BlockManager.getFrozenEdge();
        BalanceList frozenEdgeBalanceList = BalanceListManager.balanceListForBlock(frozenEdge);
        if (frozenEdgeBalanceList == null) {
            System.out.println("no balance lists available on this system");
        } else {
            long sumOfficialVerifiers = 0L;
            long sumOtherVerifiers = 0L;
            long sumGenesisAndBlock1 = 0L;

            Map<ByteBuffer, Long> balanceMap = BalanceManager.makeBalanceMap(frozenEdgeBalanceList);

            for (int i = 0; i < lockedAccountList.size(); i++) {
                long balance = balanceMap.getOrDefault(lockedAccountList.get(i), 0L);
                if (i < 10) {
                    sumOfficialVerifiers += balance;
                } else if (i < 13) {
                    sumOtherVerifiers += balance;
                } else {
                    sumGenesisAndBlock1 += balance;
                }
            }

            long seedAccountBalance = balanceMap.getOrDefault(ByteBuffer.wrap(BalanceManager.seedAccountIdentifier),
                    0L);
            long transferAccountBalance = balanceMap.getOrDefault(ByteBuffer.wrap(BalanceListItem.transferIdentifier),
                    0L);
            long cycleAccountBalance = balanceMap.getOrDefault(ByteBuffer.wrap(BalanceListItem.cycleAccountIdentifier),
                    0L);
            long totalCirculation = Transaction.micronyzosInSystem - sumOfficialVerifiers - sumOtherVerifiers -
                    sumGenesisAndBlock1 - seedAccountBalance - transferAccountBalance - cycleAccountBalance;

            CommandOutput output = new CommandOutputConsole();
            ConsoleUtil.printTable(Arrays.asList(
                    Arrays.asList("height", "official verifiers", "other verifiers", "Genesis & block-1",
                            "seed account", "transfer account", "cycle account", "circulation"),
                    Arrays.asList(frozenEdgeBalanceList.getBlockHeight() + "",
                            PrintUtil.printAmountWithCommas(sumOfficialVerifiers),
                            PrintUtil.printAmountWithCommas(sumOtherVerifiers),
                            PrintUtil.printAmountWithCommas(sumGenesisAndBlock1),
                            PrintUtil.printAmountWithCommas(seedAccountBalance),
                            PrintUtil.printAmountWithCommas(transferAccountBalance),
                            PrintUtil.printAmountWithCommas(cycleAccountBalance),
                            PrintUtil.printAmountWithCommas(totalCirculation))),
                    new HashSet<>(Arrays.asList(0, 6)), output);
        }
    }

    public static boolean isSubjectToLock(Transaction transaction) {

        return transaction.getType() != Transaction.typeCoinGeneration &&
                transaction.getType() != Transaction.typeSeed &&
                lockedAccounts.contains(ByteBuffer.wrap(transaction.getSenderIdentifier())) &&
                !ByteUtil.arraysAreEqual(transaction.getReceiverIdentifier(), BalanceListItem.cycleAccountIdentifier);
    }

    private static ByteBuffer id(String identifierString) {
        return ByteBuffer.wrap(ByteUtil.byteArrayFromHexString(identifierString, FieldByteSize.identifier));
    }
}

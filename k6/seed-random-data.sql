-- MySQL 8.0.16+ / 학술제 개발 DB 전용 더미데이터
-- 실행 전 k6를 중지하고 Workbench에서 전체 스크립트를 실행합니다.
-- 기존 일반 회원은 유지하고 k6-user-1..201@loadtest.invalid만 생성/갱신합니다.
-- 테스트 비밀번호: K6-local-test-only-2026! (k6/festival.js와 동일)
-- 회원마다 별도의 16바이트 salt + PBKDF2-HMAC-SHA256(310000회, 256비트) 해시입니다.
-- 테스트 회원의 프로필/방명록과 테스트 회원 사이 좋아요는 재실행 시 초기화됩니다.
-- 외부 회원의 좋아요는 유지하며, 좋아요 집계는 실제 레코드로 다시 계산합니다.
-- Flyway 마이그레이션이 아닙니다. SQL만 제공하며 실제 개발 DB에는 자동 실행하지 않습니다.

USE eulji_festival;
SET NAMES utf8mb4 COLLATE utf8mb4_0900_as_ci;
SET @k6_previous_safe_updates = @@SESSION.sql_safe_updates;
SET SESSION sql_safe_updates = 0;
SET @k6_seed = UUID(); -- 같은 분포를 다시 만들려면 이 값을 고정 문자열로 바꾸세요.
SET @k6_reference_time = UTC_TIMESTAMP(6);
SET @k6_email_pattern = '^k6-user-([1-9]|[1-9][0-9]|1[0-9]{2}|20[01])@loadtest[.]invalid$';

-- 배열의 순서가 k6-user-{번호}에 대응합니다. 평문 비밀번호가 저장되는 컬럼은 없습니다.
SET @k6_password_hashes = '[
  "bb3632d953068c548e3d62660d5b7d2ed3c91ce4e434975b02e68ac7df9445e1524170fd3ddf58fbd416efe9ddb9e4d3",
  "895a1b187b90766878ee5bfc84657421634b700fd50b3711ef0fb862a5690f3dd92766800c2fc1046b4fc47500f6e4e0",
  "9947d2f19cafbc1fd51380f21c5a94c794a032d9b6d9df09b1c452137048c6ec636a733b8d2e118851ba03b9d854c043",
  "1c89ba6dfd2dd35606399a7be1620390aeb1eafbdfc1ae7aad92902716cbc1793704073c2d47c52be2b725018e919b8c",
  "68cd28c0c341e529a15f0c8e3c8c9b4eb720c79153224ea29df60541cbd026a6ca73f1d1becfe116c571693745d3f4ec",
  "f8bb794c95e502c122490051782f54298b50b6deee95bc3a83437b7113dbfb15d47cd73d00aa824f787b2028bd7e6df2",
  "f5a64af07dd26423544edb726c5c9ac410e8aa425e6dc20a511c6a17aed7c528d0d4ca156fe8cc898939ffc6649ce955",
  "692ead97e9ad521ee203897e4b50c4d3ed70d89a995af62890d30a89766d8dd729e561a26f699815e2e9575666953971",
  "9ddf78f971b8b6c648e7a7e799cc93e3dcc0ab1826492978b9193a64f88abf0b3e01c2fcfeade775060709775d9f7a3e",
  "717f341212c9f3f4090587fc09ae69a23f4f6d07574c7461fd311db0de81c0a1dcb62af65198f6c40d97f1217cf2b5c3",
  "66bb0e4c550950471fd819e4307df76f463b23da572ceb6133f8cdb5e3260f3e9f82d69ca41b3889d9e39b164b0d8daf",
  "b5d09d0165be7f5d76b6d6270605acbb1ec86e7c73b1160e27ed121e40152c8bce54af94b8516df0338062dd31f9a26a",
  "2a7e83ed40f8d44c45aec7dcf2af004a84de0b780f567e220b241740c6271a18e838adb137e85854694073d88ed36314",
  "f883753c61bc74acfc3ad6e7f02943d83df782a4d7441d8a2793a906e6eb7ff210860cdf3753aebe48b3c69e91efac62",
  "e2825319a1c53c2156bcf2947422a5b7f7a0948d6624661d8628c07c758fd5275c990bedd8019bda7396ec0002c05e41",
  "278719c7618652715f7c284e9794c5d41adf040d9d50d67882e4897bef9fae9ac7b81541e69047dcdbbd09330776340f",
  "c7a776f15d6db00b2da60026e6f1cd2058c7a477fae617239a229eadaee3cacecc632ed5726f1a9b6ca4fb931cd99fff",
  "493e35d70d2592f332f8abc741fed635040ae4942d1ee33e58e54698e742437a99870e161b02ad4c3e3f8b3058f2cb79",
  "2147625d372059c0918f6af9177666a75423a56cabe7f97c7de01ecd85c0efeeea395bd7f6bbf29088cf79e59ded7fe7",
  "222612647d710c616874dcbe75c3d335cc7e29f1c5fc27c4b8ef565820268b8a0f27b69e97c5c5be9347d3c446934125",
  "00d7fa6756cd0c517aa992fef03515fe6a8dd904176ab52b3219319a6f3405895f9d008674db0a23c88b9a22b4cc005e",
  "29bb773c4074a68903d8b1c0ab5c23c60019afbf8094e915d46ab0425a675b1afd11002f39463e6fd080593f41434e6a",
  "014d7bb4c7811edac28ed94db4d93d0a6408b8b949b063432503632a103892a136ff5003475a57f128b2ff82818403ce",
  "9a452f83456004db804e83495d2f7a30e9233c46bd3fceb526a20695f212108353c76c9f567fda89e0cb85e7ef527188",
  "f49c0abd12e0fa975ff2be53564a38b21637a657274dcfb1b35236d7cd1580e266e1be06af6528da0c5fe6e17db86e91",
  "dd69734aaf37cfea21a7944a7e8e1f3d3af6345b9b40724d7b24ba5b1ce2c4e57fdc26088b9575dc3d1a8cf698c63291",
  "dff7ecaebcecbe633dc324d48d441180a64d8837229c94d13b9cdab34199845030b2cbc52438f7fe6ad14fcce3976fa2",
  "c5b5f10f9f2903621c6c7f3dcb05fb1e72724dc2a6ac39b18187add91f6e7460c06dc02c932bd97f9bb349f157aedd1f",
  "2117649f95f5a24b8b510ba6e552303dbbd96d4c344143704c42d6ea2ddf94f7be6fac80934ae7c42fe1fdb98e669409",
  "6dcbe739791e2268c39282c7072f0c55826b968ea874e8f9259b914f786bc4cefed9ff739459adc8abbe8bd1b9bc0798",
  "2cb03ffa2090bf83d3d58fa6fbf559188c203b3ee36e46f0c34394db062918082bf2aafc77feb0b73d4adadcecb059a6",
  "d12f4c2abfdab75aa48f2e154fb1d42dc8d054d69ca872f918de03355694b2ee3601f7106848cce10aca65db8448af9b",
  "9cd9b658231fc32bf1ed606e5fb5694d260a6e07c5e3eb39f62e8d46693b31afb17f28ae67f1d878f9551a27f6937832",
  "eadbde2be7a92321d51dab39f4ae2b75adaeb1d2ee615230c6e89c1f6479dd3531fc7c5d5a9192dea8b76b21bcd722d0",
  "6aa2fed497f8cb7af65f8ed989f9b7d8b00d9acde2c5d1640dd381945acf8952c900731c0f3ef13529f23680cb2aaccc",
  "604c304a7f091d3b9721d1c20624ed8ef0e5439c81ce85a0eb7b28638149edfc4ebfb73232b883fc4b9214e43522788c",
  "2411f8db903cadf609537356113bd632abf3e137088df6358ce50460479f1239d677a2a7b09bf4620c03820dd2571c98",
  "a54ff76d392cff00c59c007861820525b84f461ce1640b920597354ca2e52b73993aa22e8c5fbe0e2ecf6e2afc8b7437",
  "dbf1bf4e97e37c35488c138c02d958232ddec324fa1f19519e75c95786cd47cbfc1e82a269826535cea4fe43b1141d51",
  "1847b5ac9d2de1dd3de82f2661787a9d22d6118dcce39641b5fdaaa5491e4178b50ae34d64e2aea9ebed6e972ad62c12",
  "26825095b8f0ee9cd7ec133feda7dcab6392e2bfd921d1e62a5db92797679427ffc30f93a49030ade2e65093b85094a8",
  "9b32dc726f669c86dec3c85e937f6425259bd33b59d5af8c666fe5553e72f018b93215e49d13903812330e4863121fed",
  "a6cf3787e96f0730b54a4ed9c846d5c990ce75960c21ad74df023777d9e94a14af0737c6508a45bb7f6b301f5d7ea292",
  "5b40491431f6cf8c33ab45efa04ccce8cd5a6221550701840a034c807d022704fbd59cc85b72d3a69f44818179ae031d",
  "f072ad2c484e53797d01fd6781d08268130ac4f3422068a10bab012caaaf98ee8d5327c6759c48d0170d7cb54e5d6439",
  "fe28d003697e13d854a948b98cbe46d5612ff975cc2024e8b479b3e33c373fc04acf9965ebbcaf12ebfad23f58e658b2",
  "418f98eb634b7f0b5ada058672c2e6738ccda14f3ed4ea99b01a5472bd3c56945dc9ea6cc4018069b7ebcb0a81843cd4",
  "9845aa73869640b85f9e2b76a3d74c2e2e2cf4d5e646e1de8a5aeeef400b138dd4094ce93b5e49df44cfedabec4b8704",
  "cb0582f8d5178bef63534afdec7d293d2d091346c5648784f4c5098c7cb8e14e4a4d4abbea2bbe82ef02a611fabdbb85",
  "bd945b2a8bf0670ae9a349dc2661e2023b211547e2627ada6779d44ef66e0429c7d98446b25b8c8a4e711517e303b1a6",
  "8a7b36ae35ec1d7183632765991c77b969cd06ba6b39b9e6a1ff5ee5b01366b2a81e2986269c868c7af4f5b1203e6fae",
  "fd940767c2bbf12d92229a04a20a326a1a500cf96496dc56027f23b90805b1c3e70438e2ca05b96b07d173d60541fc40",
  "62e50de29568ca5133c130fcde32a2b63d0d6482eb21a6c9adb87075b7a724bb8b078a34104448d9920e7fba747a3da9",
  "0cf16c2a6755db9243489b56d74e0f19863b8e71dcacb2fc02025c8264fb331cbcdc7c14b821ca4c22b934222c0916d0",
  "937dcf1ee4bfda607218744d54f0270d212f78e419293daba45700cf8eb526657bbd508ee8435bcbe76104efb1cd71ae",
  "dfc774ae304137eddbec861dfc723a5eadcb9185212462b4cd74fc94f01c948e5db56d05289ce41372fec68964e69096",
  "0de97e3700f411a03787b96dc936d949982487ccc3c43503c0387dfd4850f70c274c3e8d31c0c9794309519616767129",
  "ea8657962d2896d4404a6a3cd70b3ec1713796e0b89ac867ea283e85ff0c063603372d8a4faf09b80cd32e8cdbefd653",
  "d0f0d8a381ee8b818180128216c8873f2c50669db2f448c8cc608865200c86e7004601fc6c89c25976d40945e666bcc9",
  "ce6ce071005473af428dcb98ecc24ec7925b1658af3c7046d27295b4cc0464176ab0a75f7ddd68cd0fb2b2382bc07ac6",
  "03b20fcfff061114264ed276ea771ad6c75831beb58adcddae6f7bda44175b8827fafa137b4f589a02958702ea15ed12",
  "82f97a9edc6a085ec58e59fe57b3d6d7ecc8fff7b7c4d3161a7d3c76730cfff181f56d157f5044fdfee79a110a19ce31",
  "c81dd616d8cb9e674c26882e4d9c1e7f69252323d5e8cf22a4a9a6e7c5b367d5d025e5f02a3fda596a53fa13882617c8",
  "61b1b15bfb8719515f76be14298663408bc105f5945b3919bb69587e7754d0e65005b5659e0c040028ca5b20cc2a24a3",
  "958fca93ee4a25b679d6550d0353bf0e773def4f045b5302d61ea49a4cccd645d38bc4601d3872ed9a6b9ab372eaa663",
  "8d702afa44a78c35411af2160c77d441017a5e92e13eb1e39bde23e631402f310ccddd90ad4fbd8bae989b5c4e0e091b",
  "052d4f1139530e3d7eb3204d498b741196074a899aadcf0cc810b1831ca8ef98b146d1014afc2e3d03112ec27d345b26",
  "87cfa83f6b15b3a683f24ffab4d5a16b512b355f2b1315a6e5a56f6ed04f7ca4c3e5b93f143e90b106c76cb2ee8048b3",
  "691afce238f86b88f85230d6383567bab13c477c796939c84ac4db3ba60f58ec8a4f1087e85893b7d5f5f6b950cc97ab",
  "7c6eefdf7d22d1153557f9472b1bd0869f8a2108d920a2e1761cd7549553be7ed8cc698d851206428a55c43f9b02cc90",
  "06c9032c89792b8eec73401309f4ddef79b11443bf0f67ff55b74f8047ccbea9811a1be16fe065ed64a285c726eccfb2",
  "b83009c002c4945f76b6b727b7a519da06b88a80b8820a996f78cf697d566a0e6513d82a2a1099def0a5cb75332c1273",
  "d34b254739ece572f47cc24d4d740b10068548b7c2803a6ce52fae77247e29d2c2d368f69d69683c317dff3a5ee5dbf8",
  "eab19d975665a42b0d5df4c2684ca738a704a8892db755608e87121bb7c5956b063062f1251f07e22ea5043571db30dd",
  "38ebb277e6551b57d05cd1b11ca782905b43f4f7707e5e7c19da7263add8dd985a36aa2d475c4361cedd83b52e6ccb7d",
  "a2dad730881826a21d02565ed63149fd88f378f98d2b49c01b645b18f0ca26df106911cda5249db2f20a75fb5f71e804",
  "2a05dc0af267879666af535ee6457f0071c034dbbac57f96f618764ee701c4e50b8352cd03b505a1097c795dbd5598b4",
  "f9c0af8e6ded686e1c06b123b6836bbe181473c7dc00d85bedf7907e99964fd66b6862d5360884207b82d95be392b640",
  "d4dcc4bafa51d0f4fe9dafe8507a756bb396e49013fb3f0b34b86cf45290b8e896387be1be81cdb0b1b91f214f0a2a30",
  "41f2ec067438d3e77b232a160cb6651f18d542309d663d42978813892601ca3a1a8ed4b31f0f73e4835c0cdf40607d2d",
  "b9e27c78b00a17d362402c498f300537ce0d41c8a6e041cfc11b5180e7d07fdf56317323c0bcf9d6e9600958bf37f5df",
  "b9afa90829681f68d65c2397505b554955968c9b37428157be54f06a7d2901735f6d492616279ea79e40c291d9b3d1d2",
  "bb3db5532bba9aebf20de5d10575eb937e51c670891cee704e18b7e924f9da50d7dac669dca5d1dfd58fb8d756f98ec8",
  "7f88ae822ee3fcc137cfcc6d21578ab1a363c5785fa33271154013e744592af23b74ff2634597d784a28ae265bf47e24",
  "6c4a5881926461a942e2b41b8660468a963734b75e81600a24a972d42199f4cd7dd1d8e52c0577ae6f8142709e3982e9",
  "e3b27581099b4a08dc3a90284ecd248b86acd4e94916c7c9e868a2fbbc6f6aefff5be2bc5fdad7e8e5b7e122036ea93a",
  "f668c1bcc2c950d597bed8b22d3c89bbd048c44e883dad3763999ac4c6e192bf15b166ee129beb5151f53d26b694f55b",
  "cf3b654516d0a6db231e7caee4187dee722a5a0dfebcd5d906d948d796b7ce0cc0a0ab395d89bed1fdf35dfd44411594",
  "b17134e2b0c48840caf40d17d5310aa87561aeac8905a94d06a8c480451708b9feaf7f419ab63ba1a93abde503db41e1",
  "9f34adf5c06767a514f026a5bbcf0bfe96c43580e5da56cf9462bcdc85b1e826d3ec3f552dd2aff7a92ea0e6b3607a94",
  "b1f30387ba9e646b964f3a7bb8124a94eb6a68487ab82b6b8b38c25fd96ab9790f2089b53eaa8fa65b649d3b23685387",
  "0dcc6e3ddabe572d62b2b2e52de08519265415c6048ba5f210dd4195614fb42cdbbf707c42929d515e753d1c4f6f32cd",
  "8b019f8521661a17113555c76a82fe4ed2026cd0c54a7758d93b1f7b7557111108c39ca13c3e6b6122f0bf1804b965de",
  "52beae5216a21079681271ab3ea859d373272468d4730c2ed35c4584ff338d8949b4d7b14749eec3855b158907af96ca",
  "799672cd642f50be5298f0b3c68fbd15e36772bcb0acfc8cc2b262566aa804427ef95d2f01be58f9c1c3e55879be71be",
  "a8b494e331fea74133db975924b59b771aa7724aa4df456a9e98d56c821d21f36707e0084a206cc12bc80e128300684f",
  "39a430faad345cf67416989fce5388039889a9132bf16f7832a3d0adf21a57597c9dc6d93f63e15f5342d4b702cd2c5d",
  "21a19dddd44bb80703e37b3b4574488437d0d7f95c6f6f513240f4c7bf0f231f7c2fa119bf4982a699aea43cf1174500",
  "3a9d3c89c1a610e8610342e71790fcb097f4374615e1ce0ac2896ec2255aa8f53c632e768b2b814ad402f02ef47466d7",
  "b68d654d8f29987a95d1ee7a8fe141897469a9164b27b7be008e950098d84e858e67eae6348d0c01dee85fa52a181ac6",
  "3fd92e3d8b9f9cd19a25e01729b7489cc7fbba5faa1fe4a031830f8d5f329ae23839bb27378fb6719aaa59a75f0ffaf8",
  "884dadc4251cdaac7f1905e9b8676103aaeadf5aa52dc19883a627f4d31c66f806ab635c8985ea14e6c724bac616f000",
  "15716d0a6956602b2cbb44c2f1891b9296e15d7f6c15ba075287f0b223ec60b95d8955dc121f9a5dc204d0c2241e0b83",
  "6fcfdf12a1caba057f07d1c84f47f18ba3ce0e9a649f839642817943bcfd1bda3815988ee7b7eec41aec9ef36e7259b2",
  "ae9567bdd77a3e15dc9f9199c268d74c355eb362e34712ca877c8821a0b730469df2958a29c227a0abf2a6ae7823c0f4",
  "88b8886910ead435ab309e12a356ac90d896c16e5cd9843621fe6f1f8b507f0e7f21b8ab4cce0df10f8dd2755f0c161c",
  "f20bb94390f209b36218b968d68753fe946c6a29c8197de996217d4201604328f5065487b678728afa9ddfee59da12cf",
  "82077c58d317b55ed7fde628d586b6daa8da1911e24f982712f2d8fc66cdc68d553b7e578b4ef0fc0e7d1854146d3764",
  "7cfae6af058972ffff24b358bbeec1948ab0afd28c909f34ccc6c439aa12bbdec969e1dcc59f2c2a5b9334c670f5de28",
  "7be5c7fc5fcfca6236550a9f9e1350f9d2a978c4ced581dd198e87cf4fcd525968c18e232a55a7e48422eace1b2fc491",
  "b03fad9da74381abd741f54daa79a5f0b3458c1b0f26a8bfec75323962fceab41df137511fe39408e3752ddbf2c6b8ee",
  "4cfd39c5c42bf88fdc2f43b3d939e42dba67dfbb4ee49b549ce32fbb2770db7b96cfdbc902cb096995bd7ef3513ea022",
  "4a7cec0f4d342e39fcb5ef62fe6f35d898bb8404939c1bb3291713e2b7cc8c5995dd681b028a9205ac4ecc15a34d4067",
  "8c8a044ec240d968c10dbd885bcc0abe91db442953f1c926e31ce8bfe2881cf086fc7b5df5a1528036befdbd2840241d",
  "15ffd5306efea2e39297aca5c765c72e9b41398e5c40b570616647b72276bf115c1224171459ecaa79755a0db35027d7",
  "3831a4cb8074cfd48cb931249910db7dc33545bb27909581dfb0ff9d93e9f6d2e0bdde07646d635786466734ce93ecac",
  "9bce7bf3ee226e866e7d4081196492f5deec362d734cb9b9dc5b0b6dd0525f84abe2684b590ee1a792b209c50afdf32a",
  "051e4229965d07d5fb2cd76678339488bc3a434c9ef3eded27a4552af539fd7338a87b31deceda25c0e9b22f9850504e",
  "79fe679bd87df9922c3769fdaf8b5882096fc03326fc011ac0e90df88f59db89d2848546ee495967cb2c48960fac927f",
  "a3226313277949b77a0931953c2c3c816b61a5e544b4f05c408326e2027e8297dec08f1c9b030c031a9ecd6be1b3b532",
  "7f497e9f604947f4bc024dbc652fe87d842253fbe0ab70e0d1f4472b6f0e8a04aef9a122299c523add42c7d387cee659",
  "227f09d47b32c72de66fec69136f8116633e50bf2cb3ebc7cbf6f1001e9ee7b8ba4c39649083b50f71bde2bb12ddeea5",
  "76dfbe4a6d40e4dd5956eb214f0e3f310c95a4a265ffd7abfc6575660aeafcea79943a3e8d625fefd039ea754cd0ff86",
  "ff4daa9203d19db7e9abf5865da6c710e04e93e6e423e2792788ff9f48fd5099839e90b580c3538055a46bed10631c2e",
  "40d1660858301a21e0a2dab15cdd382734a614ab94325314162669456c444d24f653a19f7ecd20d9ca34f8293fba6b72",
  "ae797833d7a746ecf64fa02b898f6db671b4799b880b10ec52a300c9a7a2d42a14dd9ac21f0cad240cf29dd5c1f4e6e0",
  "583b4124390f9c789cd58f5902d4ddd9c8803f7e0aa167ce20c37ebaa4a7961c4238d64d2e6b6a81a2bf8b5118df608f",
  "0a116efa379241a9ef2757a17fc53cdae4cbd1b479506e7d09d0b96d7f692ef6bd6758b53fa650151da84718403ef09a",
  "b2b90ed664490cffc2af79dfba599604aeb262f0084ce94ab8a312c6a4e269673cc51c645c6e0171dec2e023af37422f",
  "a83a7370a8f69f4b6ebdbde7e5681779c3b8717b368cc4102748c9f5648f66848014615d7b358f143d7c35706b8584a7",
  "0723b57d36031ff295b63f788e4b27adedeb83d34590ca2088a5db81ccc6c28f6aed153fa4027a48feff48598f354d62",
  "36ab7200f3c02dabda44b56fe8d1a956df075d638fec09321cbbd4d2a3564ef88aca822a87c84bdda04f63dc31c72d19",
  "946e5949a660649ea972532a278856814ab27295f38a923a7c8f9ac1df6616f22081a351a45e71ecbcb4a3079e6efdbd",
  "6cdb18cb7464f303f0c1c4c866387d6deca515bbccbd4fc6698f936e4fb42e3c6130758cc058686a61739a114020d49f",
  "3b78dc5a8adc551ba659b427472b7e57317bdca0e5811477a0af1c66769f6cdda9b5acc86df45c786a5444d10cb189e0",
  "ab28bdc060cdf240d24fdad20bc2fb664188ad85a84c6c34c67bf1f3dc389e6ade6633ac36b44c1026664fd34730a6f1",
  "fe2119804b090c07754b210d2014af204b8ba69560299228692fba6c2363e92ec25823f17d44f181bfe20ba49c251879",
  "6b40851d8ce2eddbacaf50c4589090d71adc6d9681a52a18bf9c3c471c76b714c6e4f53026d5117b81202816f98113b9",
  "5331436b6fadb407d91228f9d7b91e9386272eb0320d68b121c4a4f2020406db1811db53bc00d98e8bb3160aebae7c25",
  "c1a97996883318d7a8b97c4ffcd9119fe3890111fa8a26083bef2304fba6292314a42d3b16fc82464786ada8ad677c95",
  "31ec427e2a5d212e0e6ecd8df486693ab79905f2b30f17c495e5a1166a6dbe6a1e9b815951953bff7e1a812e66a91ae6",
  "aaebde3db14ddfbc856e9f68751258debfae05f18f8ad3e656d9ae21b4fd3828f953487772768677f0e73cfdb4723aa0",
  "59e7d59ad17e872d3277329bc221b400f52ea6d9e6d72acfebdd44a2f9f62e32495a8b4ca60aa273da8e700339a0157f",
  "8ac73ff6fd87857ff60a565c67474cc2e13263120af58d48cce7cb27764d7e4bddeb6cac898c4f67b6ac5e1db342b0cc",
  "6f0348ed4f5dcf506da924bbaaed08fe2699137d3d22b684087d8a12d0f747594f681a353c5c79ebf90f46c14acba8cc",
  "15ca0833ccdddde0913a585ff47c6dfaa406ad836b19cb749c5f0e253af8414ac71afc92521b08f42256807483f3ff25",
  "90a28841ba673bf942dfde54f8d2be8c3bdb7073b4993e52f3f7e9c848132cb1776ed51a0992e5d78bedbf011e6b3393",
  "1170dfb8f3a1bef4c4434bc1696ccf86d3237a63514cde59cb78a3fa0dcc397a06c8597703ff1636f01b6aed350f87d9",
  "0beb82488888ad11afc2daa7ffc53fc844c70f5591d90eba06c68fddd1281d5a0d9803116617b3ad9b2ea7804f17b7ab",
  "0157b5315ebf32a60fe3a17bee192cba8cabf064eac74c49ace076e763dd62d1cd364888611080fdc37cf1b92176df7c",
  "6a4ae022975e67bea5949e6e3e17a7d934fbd214b81cb6b3312e80532983119d0f57b19a80d3e356203ff587a4b11e01",
  "972b6d24ece75931aaa35d837d75f9b9258bb39b0779295ee3ea5ba195e6af85457bb2b00d5ace9f0b0bb8968a71c914",
  "010f403b42a52e6efa5a1816f3ae72f6a206345b5a22b169930c0ecf7b527aa7940e05b8ac2d1980c55e014b6b660d3b",
  "3e36d93ebeeb18858273659084c37961e745a5c7ee8b39c720b27136e45679a027cde651038aefd895863ac30ce6f80d",
  "ea581bda07e260144c2abde4aec3431e40c90e61a6a13877be26aaf7f5cd5a63a4f5809ee4ccc1ef5816b1deca0cd15c",
  "2bd4d13bdb3d1938113ec012e20ccf491ee3025b3e3d1f1b039b0a6bdb26ed8fb6898712a7ef127b7e5a7001b3ad9277",
  "5297bf25eff99b8ff0e5f7fbb07d1c6f6071e7b123ae7e658e2b79538d842a228cd1e0ec0a0ba58998f3f097059aecee",
  "ec2eee08a1f216fd7d1223f4b071b004f843b1f58ba1ea174a4e99410bb68b1c8cf6451b04205ff8fb53002841f2edf8",
  "1194dec0537087798df4aa34a90a5ac9c0354278eac47cbb0d6fdb7baf45b3133c642b40f4e45eba20a55d34a1b4fdbe",
  "847f3d3e18a58662fcb6f67c00915fe5a3a96d63517cfe544dc24d0360434a35258e896e5118bc28afc583261434f5c5",
  "f07c9153e486ed05cd7644d65c7c4502a2f8453ab9be891f887c186d85ec7a6f0b482674db3e768929686e522b9423e7",
  "a26225faf05699cd2d84f62dcaf12cc2c845c5cf1945b2d8730517c050aa26b9b57f1d5d7d646872708e83b2eba32da8",
  "66cc6966cbe2009ec7b1f056f959d5a2ba186e5d73c2b28be4d17050dd146bf479e4601b416bb42ce26697be22beb06c",
  "418877490a90fa658ed945712df67a11d2e30361fdede0830caabc01f20f88b1665227d773008815693f19c03d284800",
  "d18ac67e1530a11c44dd02e5ef0a4123ec2394e45161046c010cb7dce1a74ff721969faec8c63ce2651447cc313c22de",
  "e8ec823495e7f0e0e4fcd26e0521a9d7583cb90811ad322cc1d7c73e0e2f961ad6149c3bb589fcd428989b3c1b82293e",
  "b9b5b604286b59927f8f92022395cdf6239aa8d0adea187bf4fcdd4ed667b4639de1ffc4d52b1c9f6ec96a7dd2f93f1c",
  "c43423d272ddb24feefeb72a65097a30771d4dfeef1d1fd27cd2cea631fe07ee8cbe18015929c4b4ade66e4d19c64c1d",
  "2f68d2fc007593564e48ab8fb6c8aa924b738c8cfa784df7137d35b30af7e916aa3f7580c5950115cc678aaec91f0b85",
  "55aef275b5d9be9446faa7553ec2a289d8be622926318be1e83796f82e836edc552096810bb0890ddf81388325196513",
  "bd1639751c3b98bce1cc03df8b988eaca3e72a7587c3522174582fa96a2df6a2fbf0d790b3f4458d40c052df3100c96b",
  "83bd283ce3412a9cbdaf57fbc6003cb984430743835a8021fb1c2f470462066bb720789c45ee8ca52d997325da6b2254",
  "77ba7c1b60cc1e3e7cb30c60c2baac7703bf75e5ed9202a06d9f2631959b1768e04858196cf85428d38f83bd1a1a8315",
  "2392afc111adc0c322b2da8fdfb4ccbe073c27058609c702b85477a70fd5d794d174649827a90abf88d48bf3820f70a8",
  "35b5aa0370f7ccb3d5cfed388d96c1914f7e1e17f95518ca093a2ab47fd949d32bcd87a340e362d2c8879327a7eb17af",
  "69f5203af6733971471b0ca991fdc9b402fb8871bf55fb3360050a236ec713f8c395773f0c21e7bb5af1f422bbf25ad9",
  "3ab87937273d14dca2392bc8e1726396e3b635b505bc38fe89b26bfc2e4d4e2c3f03c0c6c8af387cbc57307735ec9e05",
  "ddd2792bc0ed0516b1feb2f8e1585f4c146ca8746b5591b1bff53d4d6364a85e44cbb2c6470bcb983a2fad77b71be191",
  "55450cd4048f9558d4223388fb8c1af968b518456acff4bcad8e4585a36c22242e5c4b53dc5eeeb405cf296ca8e76d5d",
  "46a6cabe079305d3cc5c5a5b0ff424c06492a553284b0fc9901a9c876be811b294716e3add3961c3556666cc4b1c18cd",
  "7e7d3fc7857f5cd09f7ff5f35f76c94def4c30019b83d778f460cc480f88a22eef5adbe03cbfd5ee3dc6cc23168f65ec",
  "a9662ceb0b5f9eed7bb243361ffd1410de46df29a688438907a4cd935009c54a00a6d6b65fb8a9ed29835276c76c684a",
  "f94212a6787baac8471c1d94bee88e8f5e4999cf3c374494b68123261cfae36877ceed370b57dc4b5def3acb66fefea4",
  "8f6181ca2db5e25ecd0da39e3155567cc4a1a44dc20379abdd05d4c796998767bd8fd35a9f825e8adccc272182fe7146",
  "f183dbd174c94e5b988204ecdb0b013e85cda9f1da54e3ebbab1260294c6db607245ddeb4ab7021e2d3e8a35954f8187",
  "83fa5a7489f0811a268d348ef30d049a2eadaffa900b44eafaba560c5b4ebdabd5fa623cf09874cec1ea880246077d6c",
  "851918915331322dc1f54a3036c66ca7be2d62c59fa8e4fda3a32fe6815653aae9233f2ce5ac7543e35bf5af6528c4ee",
  "9a02294d0222133e5bac3d13f80e74835e65b19af33c613cd31d2634c7a322aa7e16cc4447ae0dcdbbdba93d7aff653a",
  "4b0690e9b7030dad1e799ffadfd64fb5a4da6a8b4425bda293aa7cd24ddc384d6750266c366eebdd0e22f35606a9cd03",
  "f64cd55b82e9d411e935c8dd9e716fb15f78571416483857808c5a0edf540533ee22ddcbbc022c9c458bb5f11040ab05",
  "687cd0f44ff63a2120d725864520ee70980ea08cffff603c65fefbd1764f8d504cee05e54ca44eec9c5fcc67d685f7da",
  "2ab3a53890007b1b8df2b1a1e2f6a4f25bce6fb82be9583ac661e11b7a7afdbd2b79d820b8b41b2785575fd1e58de4c6",
  "af746b90fc8b317c507d40cc5143e18b0b6e38d2a0d4e2362073626ad0705a20bb0b4b3c12ff13038566e302829d4f26",
  "5e509c64d7636ee7eead17d5b8ef8bb1708fd0ae02c0d6d6e2a5c3fbf3b178dcdaa2044d7905361f596d63b2d3cfeefc",
  "c792325f78b3da4855644931dedfaec05709ee5a6047341b0ae349d6dfc932c4693ab9c6db2cb0a8cdce29f36ad9f548",
  "cc25ac0eece6bf462e18ce1a1946a670f26b1f038114ee93a6810afbe93385799c34cd22e669457719dd95e4de469e03",
  "73eada8aa7115ce4159b129948b394865a4eb07c981bf59f0eb99ca7fefcb4063f883909670f8e8ea9029e196f589619",
  "4d059ab9acb1a4619d6eae15f56f9ca29576d3cff28f9b52643298284c801c532acd880007d320d53d25ec2fddd57a56",
  "5439c68d0145319df085dbcfc1ce5b651a7c7337b85e07b9c848104a973842d1d7315513aedb1c01a86930864d600f20",
  "62eb2a724cae090e99ba76a8e3e52df207199f6e9783a3880ad1ef0f5ba64f5e8af1e0ace7cf569281ad8a61f421222c",
  "7a308fa0a285988020afb85c527fb36ad867f28c7c789771ba1f0e3080ab4d6ec8acb632a96de7240d3d9018c3e14289"
]';

START TRANSACTION;

-- 1. 200명의 VU 계정 + 추가 조회/좋아요 대상 1명
-- CRC32의 입력에 매번 달라지는 seed를 포함해 이름, 캐릭터, 학년을 분산합니다.
INSERT INTO members (email, password_hash, character_id, name, grade)
SELECT
    CONCAT('k6-user-', fixture.n, '@loadtest.invalid'),
    fixture.password_hash,
    1 + MOD(CRC32(CONCAT(@k6_seed, ':character:', fixture.n)), 6),
    CONCAT(
        ELT(1 + MOD(CRC32(CONCAT(@k6_seed, ':surname:', fixture.n)), 10),
            '김', '이', '박', '최', '정', '강', '조', '윤', '장', '임'),
        ELT(1 + MOD(CRC32(CONCAT(@k6_seed, ':name:', fixture.n)), 12),
            '민준', '서연', '지우', '하준', '도윤', '수빈', '지민', '예린', '서준', '하윤', '유진', '시우'),
        ' 테스트', fixture.n
    ),
    ELT(1 + MOD(CRC32(CONCAT(@k6_seed, ':grade:', fixture.n)), 6),
        '1학년', '2학년', '3학년', '4학년', '졸업생', '게스트')
FROM JSON_TABLE(@k6_password_hashes, '$[*]' COLUMNS (
    n FOR ORDINALITY,
    password_hash VARCHAR(96) PATH '$'
)) AS fixture
ORDER BY fixture.n
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    character_id = VALUES(character_id),
    name = VALUES(name),
    grade = VALUES(grade);

-- 2. 회원당 방명록 1개. 작성 시각은 실행 시각 기준 최근 7일 안으로 분산합니다.
INSERT INTO guestbook_entries (member_id, message, created_at)
SELECT
    m.id,
    CONCAT(
        ELT(1 + MOD(CRC32(CONCAT(@k6_seed, ':message:', fixture.n)), 12),
            '학술제 개최를 축하합니다!',
            '다양한 작품을 구경할 수 있어서 즐거웠어요.',
            '발표를 준비하신 모든 분들 수고하셨습니다.',
            '친구들과 함께 좋은 추억을 만들었습니다.',
            '흥미로운 프로젝트가 많아서 시간이 금방 지나갔네요.',
            '처음 방문했는데 정말 재미있었습니다!',
            '후배들의 멋진 도전을 응원합니다.',
            '다음 학술제에도 꼭 다시 참여하고 싶어요.',
            '아이디어부터 구현까지 정말 인상적이었습니다.',
            '체험 프로그램이 재미있어요. 모두 파이팅! 🎉',
            '열심히 준비한 결과를 공유해 주셔서 감사합니다.',
            '서로 배우고 성장하는 시간이 되었으면 좋겠습니다.'),
        ' [테스트 ', fixture.n, ']'
    ),
    TIMESTAMPADD(SECOND,
        -MOD(CRC32(CONCAT(@k6_seed, ':created-at:', fixture.n)), 604801), @k6_reference_time)
FROM JSON_TABLE(@k6_password_hashes, '$[*]' COLUMNS (n FOR ORDINALITY)) AS fixture
JOIN members m ON m.email = CONCAT('k6-user-', fixture.n, '@loadtest.invalid')
ORDER BY m.id
ON DUPLICATE KEY UPDATE
    message = VALUES(message),
    created_at = VALUES(created_at);

-- 3. 지정한 201개 테스트 계정 사이의 좋아요만 제거합니다.
-- 일반 회원이 테스트 방명록에 누른 좋아요, 테스트 회원이 일반 회원에게 누른 좋아요는 유지합니다.
DELETE likes
FROM guestbook_likes likes
JOIN members target_member ON target_member.id = likes.entry_member_id
JOIN members liker_member ON liker_member.id = likes.liker_member_id
WHERE target_member.email REGEXP @k6_email_pattern
  AND liker_member.email REGEXP @k6_email_pattern;

-- 4. 무작위로 인기 회원과 좋아요를 누를 회원을 선정합니다.
-- 상위: 140개 / 100개 / 100개 (공동 2위)
-- 하위 20명: 0개. 나머지는 0~69개로 분산합니다.
-- 자기 좋아요는 제외하며, PK(entry_member_id, liker_member_id)로 중복되지 않습니다.
INSERT INTO guestbook_likes (entry_member_id, liker_member_id)
WITH test_members AS (
    SELECT m.id AS member_id, fixture.n
    FROM JSON_TABLE(@k6_password_hashes, '$[*]' COLUMNS (n FOR ORDINALITY)) AS fixture
    JOIN members m ON m.email = CONCAT('k6-user-', fixture.n, '@loadtest.invalid')
), popularity AS (
    SELECT member_id,
        ROW_NUMBER() OVER (ORDER BY CRC32(CONCAT(@k6_seed, ':popularity:', n)), n) AS popularity_order
    FROM test_members
), targets AS (
    SELECT member_id,
        CASE
            WHEN popularity_order = 1 THEN 140
            WHEN popularity_order IN (2, 3) THEN 100
            WHEN popularity_order > 181 THEN 0
            ELSE FLOOR(70 * POWER(CRC32(CONCAT(@k6_seed, ':count:', member_id)) / 4294967296.0, 2))
        END AS desired_likes
    FROM popularity
), candidates AS (
    SELECT target.member_id AS entry_member_id, liker.member_id AS liker_member_id,
        target.desired_likes,
        ROW_NUMBER() OVER (
            PARTITION BY target.member_id
            ORDER BY SHA2(CONCAT(@k6_seed, ':liker:', target.member_id, ':', liker.member_id), 256), liker.member_id
        ) AS selection_order
    FROM targets target
    CROSS JOIN test_members liker
    WHERE target.member_id <> liker.member_id
)
SELECT entry_member_id, liker_member_id
FROM candidates
WHERE selection_order <= desired_likes;

-- 5. 보존한 외부 좋아요도 포함해 실제 좋아요 레코드 수로 집계를 맞춥니다.
UPDATE guestbook_entries entry
JOIN members m ON m.id = entry.member_id
SET entry.like_count = (
    SELECT COUNT(*) FROM guestbook_likes likes WHERE likes.entry_member_id = entry.member_id
)
WHERE m.email REGEXP @k6_email_pattern;

COMMIT;
SET SESSION sql_safe_updates = @k6_previous_safe_updates;

-- 어떤 문장에서 오류가 발생하면 계속 실행하지 말고 ROLLBACK; 후 원인을 확인하세요.
-- 이후 SET SESSION sql_safe_updates = @k6_previous_safe_updates; 로 세션 설정을 복구합니다.

-- 6. 결과 확인
SELECT @k6_seed AS random_seed, @k6_reference_time AS generated_at_utc;

SELECT COUNT(*) AS test_member_count,
    COUNT(entry.member_id) AS test_guestbook_count,
    MIN(entry.created_at) AS oldest_created_at_utc,
    MAX(entry.created_at) AS newest_created_at_utc,
    MAX(CHAR_LENGTH(entry.message)) AS longest_message_chars
FROM members m
LEFT JOIN guestbook_entries entry ON entry.member_id = m.id
WHERE m.email REGEXP @k6_email_pattern;

SELECT m.character_id, COUNT(*) AS member_count
FROM members m WHERE m.email REGEXP @k6_email_pattern
GROUP BY m.character_id ORDER BY m.character_id;

SELECT m.grade, COUNT(*) AS member_count
FROM members m WHERE m.email REGEXP @k6_email_pattern
GROUP BY m.grade ORDER BY m.grade;

SELECT m.id AS member_id, m.name, m.grade, entry.like_count,
    RANK() OVER (ORDER BY entry.like_count DESC) AS test_pool_rank
FROM members m
JOIN guestbook_entries entry ON entry.member_id = m.id
WHERE m.email REGEXP @k6_email_pattern
ORDER BY entry.like_count DESC, entry.created_at DESC, m.id
LIMIT 10;

-- 아래 쿼리는 집계가 맞으면 0행이어야 합니다.
SELECT entry.member_id, entry.like_count AS stored_like_count,
    (SELECT COUNT(*) FROM guestbook_likes likes WHERE likes.entry_member_id = entry.member_id) AS actual_like_count
FROM guestbook_entries entry
JOIN members m ON m.id = entry.member_id
WHERE m.email REGEXP @k6_email_pattern
HAVING stored_like_count <> actual_like_count;

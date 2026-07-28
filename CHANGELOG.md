# Changelog

## [0.3.0](https://github.com/SheepFromHeaven/mine-now/compare/v0.2.0...v0.3.0) (2026-07-28)


### Features

* **client:** add debug tab to structure menu ([d3ce92d](https://github.com/SheepFromHeaven/mine-now/commit/d3ce92d4882e0f49b987e0c227c947cb62d21537))
* **client:** add head rotation layer for inhabitants ([e8a72fc](https://github.com/SheepFromHeaven/mine-now/commit/e8a72fcc6515502bd04d83634d912952d95bcf03))
* **client:** add inhabitant debug view and per-inhabitant issues screen ([1fb279c](https://github.com/SheepFromHeaven/mine-now/commit/1fb279c6507d98af9a96499799bad6a8d55e17ca))
* **client:** add searchable inhabitant selection screen for assignments ([6df0de7](https://github.com/SheepFromHeaven/mine-now/commit/6df0de71a22f41cd03aae65e96be88199e7d9670))
* **command:** add /minenow debugvillage dev command ([8fdbb13](https://github.com/SheepFromHeaven/mine-now/commit/8fdbb135bdbb63c3fcceefde5598627e1a5a6972))
* **command:** stock debug village barrels with required tools ([ddb3adb](https://github.com/SheepFromHeaven/mine-now/commit/ddb3adb45d28964def33b3c38ea6de132150293b))
* **dominion:** require a bed before assigning an inhabitant to a house ([2831321](https://github.com/SheepFromHeaven/mine-now/commit/283132197cac9f7819ed81efe11109fe5a153ff2))
* **entity:** add FinishWorkGoal to stow job tools at nightfall ([cb25b7c](https://github.com/SheepFromHeaven/mine-now/commit/cb25b7c5d67160228784c46f1786972c94ff7dac))
* **entity:** decay and restore the rest need with sleep ([11792e3](https://github.com/SheepFromHeaven/mine-now/commit/11792e3bc428d7a96256ee51a329f196ea1d5e4e))
* **entity:** play villager sound when inhabitants switch goals ([e8d0884](https://github.com/SheepFromHeaven/mine-now/commit/e8d0884c4b1ebb1d1f96d156f4fa44c13962dc98))
* **item:** add Tree Scanner Wand debug tool ([b8cf0c1](https://github.com/SheepFromHeaven/mine-now/commit/b8cf0c1912686265e21578182452213253ede038))
* **job:** cook using the tavern's real smoker block ([18965bd](https://github.com/SheepFromHeaven/mine-now/commit/18965bd76aca04ca2979c087a5f36d421f754d13))
* **job:** flood-fill tree trunks by log species with configurable max size ([86c07d7](https://github.com/SheepFromHeaven/mine-now/commit/86c07d770c2e1e3a50b4534d3d25b153345a484d))
* **job:** lumberjack harvests canopy leaves after felling a tree ([4f6cae9](https://github.com/SheepFromHeaven/mine-now/commit/4f6cae929456d3e79dce4c4b280e3e60e86d9ca3))
* **job:** scan for a better fishing shore and avoid unreachable water ([9200ec5](https://github.com/SheepFromHeaven/mine-now/commit/9200ec505af3b15cef296c5fcfda7353c23483fe))
* **job:** turn inhabitants to face the block they're working on ([6bcfdcb](https://github.com/SheepFromHeaven/mine-now/commit/6bcfdcbcad2b1ed1185ce21ca6a5f3f9c3aafb8b))
* **job:** walk to storage containers instead of teleporting items ([e5e0047](https://github.com/SheepFromHeaven/mine-now/commit/e5e00470f3b14e61ba17a9c927dacaf1e1c215ae))
* **lumberyard:** add configurable work area with world-pick UI ([c381f88](https://github.com/SheepFromHeaven/mine-now/commit/c381f88730c5a40963f6627077c9919dfff3f44c))
* **tavern:** add cook job that prepares and serves food ([9ff6f4f](https://github.com/SheepFromHeaven/mine-now/commit/9ff6f4fdf5aaba59c0125d3868f887b2f9534c0b))


### Bug Fixes

* **client:** correct renderEntityInInventoryFollowsMouse argument order ([69e6c82](https://github.com/SheepFromHeaven/mine-now/commit/69e6c82b7e8c4eaccfa9fd17507129af383b8df2))
* **dominion:** anchor sleeping position on bed foot half ([8058cc6](https://github.com/SheepFromHeaven/mine-now/commit/8058cc6368d931ec0ab7407b22c5f0d4b56e2fb1))
* **gametest:** place work-area test tree within structure bounds ([f571350](https://github.com/SheepFromHeaven/mine-now/commit/f5713508620bb31b56ae1f602c79edf745b875f3))
* **job:** approach nearest standable tile when marker isn't on floor ([43fae11](https://github.com/SheepFromHeaven/mine-now/commit/43fae118e25d06ccc008f7ffe90bdf29f642303f))
* **job:** ignore grass and flowers touching a tree trunk ([a69ed17](https://github.com/SheepFromHeaven/mine-now/commit/a69ed175ccf83ab848bfe36f0c99b62a710fe7f9))
* **job:** re-approach workplace after a job is interrupted or unassigned ([5a3ad13](https://github.com/SheepFromHeaven/mine-now/commit/5a3ad1391872c6221a1cebd574b5da73d4572846))
* **job:** re-fetch fuel when the smoker burns out mid-cook ([af14d0a](https://github.com/SheepFromHeaven/mine-now/commit/af14d0a635e8ab9e914842e0ca63d88c5e48fce4))
* **job:** retry finding a tree on a cooldown instead of every tick ([af4fb90](https://github.com/SheepFromHeaven/mine-now/commit/af4fb906388f87c27aff56eb293e8d6033444e52))
* **job:** stop lumberjack from getting stuck at an unreachable tree ([2f6ead1](https://github.com/SheepFromHeaven/mine-now/commit/2f6ead1d9f005a079cfc15738745a9e6a157f919))

## [0.2.0](https://github.com/SheepFromHeaven/mine-now/compare/v0.1.0...v0.2.0) (2026-07-27)


### Features

* add dedicated creative tab and fix item icons ([be7b201](https://github.com/SheepFromHeaven/mine-now/commit/be7b201701c1bb3e2455fdbd5cd92193452f4ceb))
* add dominion system, town banner, structure marking, and config screen ([9d37ef0](https://github.com/SheepFromHeaven/mine-now/commit/9d37ef06b980bd739a9310605a082cd1e4babc2c))
* add inhabitant system with recruitment, needs, and go-home behavior ([512dd05](https://github.com/SheepFromHeaven/mine-now/commit/512dd0579258ec49cd0177a614a57bed2d2963aa))
* add structure requirements system with recalculate and configurable JSON config ([513b1ba](https://github.com/SheepFromHeaven/mine-now/commit/513b1bad840297cafb3e5880d99a9f7e97bb69ce))
* **client:** render structure marker range as filled boundary walls ([42f55ba](https://github.com/SheepFromHeaven/mine-now/commit/42f55bafbd5a278b55fe63faadd0a459f788f9c4))
* **client:** switch dominion/inhabitant/structure UIs to vanilla styling ([85c5407](https://github.com/SheepFromHeaven/mine-now/commit/85c5407e97e733a2550f744d4b339d2133ef8e3c))
* **dominion:** reconvert inhabitants to villagers on disband ([969b644](https://github.com/SheepFromHeaven/mine-now/commit/969b644efe6ea22a7dc88f5dbca5af2fce6d20f8))
* **dominion:** reserve assigned beds from vanilla villagers ([bc01569](https://github.com/SheepFromHeaven/mine-now/commit/bc01569b26b6fe938bec86bfa3191f96077475fe))
* **entity:** add job system with fisher job for inhabitants ([322e34f](https://github.com/SheepFromHeaven/mine-now/commit/322e34fd251a4cf7ffd0ca3a48dc612865396b5a))
* **entity:** inhabitants sleep in beds and repath while going home ([2c196ab](https://github.com/SheepFromHeaven/mine-now/commit/2c196abcf1161f2dc4a920da9f4d89df5a7959e9))
* **fishery:** deposit catch only into #minenow:storage blocks ([4824e3f](https://github.com/SheepFromHeaven/mine-now/commit/4824e3fe9968e311e9e320b2443ed42f28090872))
* initialize MineNow Forge mod project for 1.21.11 ([978c6d9](https://github.com/SheepFromHeaven/mine-now/commit/978c6d97aa83f58623b555b1ef8458c377daac98))
* **inventory:** add dedicated inhabitant inventory menu ([606f61f](https://github.com/SheepFromHeaven/mine-now/commit/606f61f3e2293727c3c5eb3f649d798f3fb1b79f))
* **lumberyard:** add lumberjack job with tree scanning ([c0f6db5](https://github.com/SheepFromHeaven/mine-now/commit/c0f6db529a2ba04599374cb973edcfb9fab394cb))
* **mod:** add MineNow main class and config ([3afd092](https://github.com/SheepFromHeaven/mine-now/commit/3afd092f918a30f2a365275043495ea4ebf83084))
* render structure markers in world and expand structure/inhabitant logic ([a0608df](https://github.com/SheepFromHeaven/mine-now/commit/a0608df87134b65a69c49e3f3c76fd3ba457d203))


### Bug Fixes

* **client:** hide outer panel frame bevel on rows without a tool slot ([33fd3b3](https://github.com/SheepFromHeaven/mine-now/commit/33fd3b3b5972a778345ea273be4df971eae4ffca))
* **client:** patch tool slot's trailing border in vanilla row inventory ([dc3d5e9](https://github.com/SheepFromHeaven/mine-now/commit/dc3d5e93fb7dc10962abbd8b7df3b006c8ec0de5))
* **dist:** stop dedicated-server crash from client-only class refs ([ce762fd](https://github.com/SheepFromHeaven/mine-now/commit/ce762fd111dc44749db3f3c5fafd018e2dddfe9a))
* **entity:** let inhabitants detour around obstacles and open gates ([93b81cc](https://github.com/SheepFromHeaven/mine-now/commit/93b81cc6e74f16756ea9a7f54b8145e443e97f4d))
* **fishery:** match vertical water search range to horizontal radius ([cfb1e91](https://github.com/SheepFromHeaven/mine-now/commit/cfb1e91e7425fab6c1b0b13f0955b42a5b3bad44))
* **lang:** add item.* names for town_banner and structure_marker ([7ec07f5](https://github.com/SheepFromHeaven/mine-now/commit/7ec07f52b14bfa7f22061f72d15991919f4feb66))
* **tags:** move storage tag into tags/block folder ([6aff6ee](https://github.com/SheepFromHeaven/mine-now/commit/6aff6ee4c1ed47f7a20df7b744a9e0ee3b59b038))

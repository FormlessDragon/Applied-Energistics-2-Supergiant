# Applied Energistics 2 Supergiant

Applied Energistics 2 is a Minecraft mod about Matter, Energy and using them to conquer the world.

This repository is a Minecraft 1.12.2 Cleanroom migration of Applied Energistics 2. It adapts AE2's storage, networking, crafting, terminal, rendering, and addon API systems to the target runtime.

## Upstream

This project is based on [AppliedEnergistics/Applied-Energistics-2](https://github.com/AppliedEnergistics/Applied-Energistics-2).

The upstream repository remains the original AE2 project and the primary reference for design, behavior, licensing, and attribution.

## Documentation

* [API.md](API.md): API notes for this branch
* [Upstream repository](https://github.com/AppliedEnergistics/Applied-Energistics-2)

## Building

Use the Gradle wrapper from the repository root.

```sh
./gradlew build
```

On Windows:

```bat
gradlew.bat build
```

The build output is written to `build/libs`.

## Integrated

* [Lapis256/AE2ToggleableViewCell](https://github.com/Lapis256/AE2ToggleableViewCell/tree/1.21.1): integrated the toggleable view cell feature with partial code reuse under the MIT License.

## License

* Applied Energistics 2 API
  * (c) 2013 - 2020 AlgorithmX2 et al
  * [![License](https://img.shields.io/badge/License-MIT-red.svg?style=flat-square)](http://opensource.org/licenses/MIT)
* Applied Energistics 2
  * (c) 2013 - 2020 AlgorithmX2 et al
  * [![License](https://img.shields.io/badge/License-LGPLv3-blue.svg?style=flat-square)](LICENSE)
* Textures and Models
  * (c) 2020, [Ridanisaurus Rid](https://github.com/Ridanisaurus/), (c) 2013 - 2020 AlgorithmX2 et al
  * [![License](https://img.shields.io/badge/License-CC%20BY--NC--SA%203.0-yellow.svg?style=flat-square)](https://creativecommons.org/licenses/by-nc-sa/3.0/)
* Text and Translations
  * [![License](https://img.shields.io/badge/License-No%20Restriction-green.svg?style=flat-square)](https://creativecommons.org/publicdomain/zero/1.0/)
* Integrated toggleable view cell portion
  * Derived in part from [Lapis256/AE2ToggleableViewCell](https://github.com/Lapis256/AE2ToggleableViewCell/tree/1.21.1)
  * [![License](https://img.shields.io/badge/License-MIT-red.svg?style=flat-square)](http://opensource.org/licenses/MIT)
* Additional Sound Licenses
  * Guidebook Click Sound
    * [EminYILDIRIM](https://freesound.org/people/EminYILDIRIM/sounds/536108/)
    * [![License](https://img.shields.io/badge/License-CC%20BY%204.0-yellow.svg?style=flat-square)](https://creativecommons.org/licenses/by/4.0/)

## Credits

Thanks to Team Applied Energistics, AlgorithmX2, the upstream AE2 contributors, and everyone involved in the original project:
[AppliedEnergistics/Applied-Energistics-2](https://github.com/AppliedEnergistics/Applied-Energistics-2).

/**
 * Example icon pack. Keys are asset names, values are image files relative to the plugin's directory.
 * Gradle packs `assets/` into the ZIP.
 *
 * - `ClydeIcon`: Discord icon (24dp). Discord tints most icons, so only the star shape shows.
 * - `logo`: Discord logo (120dp), shown on startup screens.
 * - `RevengeIcon`: Revenge's own bundled asset, eg. the Revenge row in Settings.
 */

/// <reference types="@revenge-mod/themes-api" />

export default plugin({
	preInit({ themes }) {
		themes.registerGroup('examples', {
			name: 'Examples',
			description: 'Icon packs showing what can be replaced',
		})

		themes.registerIconPack('examples', 'example', {
			name: 'Example',
			description: 'Custom images for Discord and Revenge icons',
			icons: {
				ClydeIcon: 'assets/icons/ClydeIcon.png',
				RevengeIcon: 'assets/icons/RevengeIcon.png',
			},
		})
	},
})

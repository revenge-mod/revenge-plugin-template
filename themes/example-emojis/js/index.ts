/**
 * Example emoji packs.
 *
 * - System: draws every emoji with the device's own emoji font.
 * - Example: custom images for 😀, ❤️ and 👍. Other emojis keeps Discord's default.
 *
 * Images are named like Discord's: `emoji-<codepoints>.png`. Gradle packs `assets/` into the ZIP.
 */

/// <reference types="@revenge-mod/themes-api" />

export default plugin({
	preInit({ themes }) {
		themes.registerGroup('examples', {
			name: 'Examples',
			description: 'Emoji packs showing images and fonts',
		})

		themes.registerEmojiPack('examples', 'system', {
			name: 'System',
			description: "Your device's own emoji",
			font: 'system',
		})

		themes.registerEmojiPack('examples', 'example', {
			name: 'Example',
			description: 'Custom images for 😀, ❤️ and 👍',
			images: 'assets/emoji',
		})
	},
})

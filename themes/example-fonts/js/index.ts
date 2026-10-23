/**
 * Example font pack. Replaces Discord's gg sans with Google Sans Flex (SIL OFL 1.1, see GoogleSansFlex-LICENSE).
 *
 * Google Sans Flex is one variable font file. Each variant sets the `wght` axis, and italic
 * variants also set `slnt`, since `weight` and `italic` alone only label a variant.
 *
 * For static fonts, you can point to individual files for each variant. See the commented example below.
 *
 * Paths are relative to the plugin's directory. Gradle packs `assets/` into the ZIP.
 */

/// <reference types="@revenge-mod/themes-api" />

import type { FontVariant } from '@revenge-mod/themes-api/types'

const Font = 'assets/fonts/GoogleSansFlex.ttf'

/** Discord asks for these family names. The style is part of the name. */
const Weights: Record<string, number> = {
	Normal: 400,
	Medium: 500,
	Semibold: 600,
	Bold: 700,
	ExtraBold: 800,
}

const families: Record<string, FontVariant[]> = {}
for (const [style, wght] of Object.entries(Weights))
	families[`ggsans-${style}`] = [
		{ path: Font, weight: wght, axes: { wght } },
		{ path: Font, weight: wght, italic: true, axes: { wght, slnt: -10 } },
	]

export default plugin({
	preInit({ themes }) {
		themes.registerGroup('examples', {
			name: 'Examples',
			description: 'Example font packs',
		})

		themes.registerFontPack('examples', 'google-sans-flex', {
			name: 'Google Sans Flex',
			description: 'Variable font, replaces gg sans',
			// Families is declared above and generated programatically by a for loop.
			families,
		})

		// Static font example
		/*

		themes.registerFontPack('examples', 'static-font', {
			name: 'Static Font',
			description: 'Static font, replaces gg sans',
			families: {
				'ggsans-Normal': [
					{ path: 'assets/...', weight: 400 },
		       		{ path: 'assets/...', weight: 400, italic: true },
				],
				'ggsans-Medium': [
					{ path: 'assets/...', weight: 500 },
		         	{ path: 'assets/...', weight: 500, italic: true },
				],
		     	// Add more variants as needed...
			},
		})

		*/
	},
})

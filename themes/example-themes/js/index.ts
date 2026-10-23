/**
 * Example themes and backgrounds background.
 * Replace or derive from Discord's default theme. Backgrounds shows as an image behind the main screens and chat.
 *
 * Paths are relative to the plugin's directory. Gradle packs `assets/` into the ZIP.
 */

/// <reference types="@revenge-mod/themes-api" />

import { Tokens } from '@revenge-mod/discord/common/tokens'
import type { ThemeColors } from '@revenge-mod/themes-api/types'

export default plugin({
	preInit({ themes }) {
		themes.registerGroup('examples', {
			name: 'Examples',
			description: 'Example themes and backgrounds',
		})

		const Image = 'assets/background.png'

		themes.registerTheme('examples', 'stripes', {
			name: 'Stripes',
			// Returning an empty object/record means no color overrides.
			getColors: () => ({}),
			background: { path: Image },
		})

		themes.registerTheme('examples', 'stripes-soft', {
			name: 'Stripes, dimmed and blurred',
			// Returning an empty object/record means no color overrides.
			getColors: () => ({}),
			background: { path: Image, opacity: 0.5, blur: 8, dimming: 0.5 },
		})
	},
	init({ themes }) {
		// If you need access to Discord's token names, do it in `init` or `start`!

		// Make all semantic tokens starting with `TEXT_` red.
		themes.registerTheme('examples', 'red-text', {
			name: 'Make Text Red',
			getColors: () => {
				const tokenNames = Object.keys(Tokens.SemanticColor).filter(name =>
					name.startsWith('TEXT_'),
				)

				const mySemanticColors: ThemeColors['semantic'] = {
					dark: {},
					darker: {},
					light: {},
					midnight: {},
				}

				for (const name of tokenNames) {
					mySemanticColors.dark![name] = '#ff0000'
					mySemanticColors.darker![name] = '#ff0000'
					mySemanticColors.light![name] = '#ff0000'
					mySemanticColors.midnight![name] = '#ff0000'
				}

				return {
					semantic: mySemanticColors,
				}
			},
		})

		// You can also make derivable themes (e.g. adding color tint or dimming).
		/*
		themes.registerTheme('examples', 'example-2', {
			name: 'Make Background Red',
			// The discord variable provides just raw colors without any overrides.
			// You can use them to derive your own colors.
			getColors: discord => {
				const myRawColors: ThemeColors['raw'] = {}

				// Your own color logic...

				return {
					raw: myRawColors,
				}
			},
		})
		*/
	},
})

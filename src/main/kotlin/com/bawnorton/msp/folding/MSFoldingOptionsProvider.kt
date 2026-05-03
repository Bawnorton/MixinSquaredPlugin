package com.bawnorton.msp.folding

import com.intellij.application.options.editor.CodeFoldingOptionsProvider
import com.intellij.openapi.options.BeanConfigurable

class MSFoldingOptionsProvider : BeanConfigurable<MSFoldingSettings.State>(MSFoldingSettings.instance.state), CodeFoldingOptionsProvider {
    init {
        title = "MixinSquared"

        val settings = MSFoldingSettings.instance
        checkBox(
            "@TargetHandler's fields",
            settings.state::foldTargetHandler
        )
    }
}
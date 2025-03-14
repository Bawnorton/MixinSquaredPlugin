package com.bawnorton.msp.folding

import com.intellij.application.options.editor.CodeFoldingOptionsProvider
import com.intellij.openapi.options.BeanConfigurable

class MSFoldingOptionsProvider : BeanConfigurable<MSFoldingSettings.State>(MSFoldingSettings.instance.state), CodeFoldingOptionsProvider {
    init {
        title = "MixinSquared"

        val settings = MSFoldingSettings.instance
        checkBox(
            "@TargetHandler mixin field",
            { settings.state.foldTargetHandlerMixin },
            { b -> settings.state.foldTargetHandlerMixin = b },
        )
    }
}
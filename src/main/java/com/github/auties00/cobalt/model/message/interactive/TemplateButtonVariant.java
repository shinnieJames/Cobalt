package com.github.auties00.cobalt.model.message.interactive;

public sealed interface TemplateButtonVariant permits TemplateButton.QuickReplyButton, TemplateButton.URLButton, TemplateButton.CallButton {
}

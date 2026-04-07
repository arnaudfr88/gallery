package com.google.ai.edge.gallery.service

import javax.inject.Inject

class ModelManagerHolder @Inject constructor() {
    var manager: ModelManagerAccessor? = null
        get() = field
        set(value) {
            field = value
        }
}

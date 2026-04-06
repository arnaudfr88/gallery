package com.google.ai.edge.gallery.service

import com.google.ai.edge.gallery.data.Model

interface ModelManagerAccessor {
    fun getDownloadedModelNames(): List<String>
    fun getModelByName(name: String): Model?
}
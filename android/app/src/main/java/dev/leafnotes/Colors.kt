package dev.leafnotes

import androidx.compose.ui.graphics.Color

val accentChoices = listOf("Yellow", "Blue", "Purple", "Pink", "Green", "Orange")
fun accentColor(name:String,dark:Boolean):Color = Color(when(name) {
    "Blue" -> if(dark)0xFF0091FF else 0xFF0088FF
    "Purple" -> if(dark)0xFFDB34F2 else 0xFFCB30E0
    "Pink" -> if(dark)0xFFFF375F else 0xFFFF2D55
    "Green" -> if(dark)0xFF30D158 else 0xFF34C759
    "Orange" -> if(dark)0xFFFF9230 else 0xFFFF8D28
    else -> if(dark)0xFFFFD600 else 0xFFFFCC00
})

data class LeafColors(val page: Color, val paper: Color, val canvas: Color, val fill: Color, val text: Color, val secondary: Color, val tertiary: Color, val separator: Color, val accent: Color, val danger: Color, val dark: Boolean)
val lightLeafColors = LeafColors(page = Color(0xFFF2F2F7), paper = Color(0xFFFFFFFF), canvas = Color(0xFFFFFFFF), fill = Color(0x1F767680), text = Color(0xFF000000), secondary = Color(0x993C3C43), tertiary = Color(0x4D3C3C43), separator = Color(0x1F000000), accent = Color(0xFFFFCC00), danger = Color(0xFFFF383C), dark = false)
val darkLeafColors = LeafColors(page = Color(0xFF000000), paper = Color(0xFF1C1C1E), canvas = Color(0xFF000000), fill = Color(0x3D767680), text = Color(0xFFFFFFFF), secondary = Color(0xB2EBEBF5), tertiary = Color(0x4DEBEBF5), separator = Color(0x2BFFFFFF), accent = Color(0xFFFFD600), danger = Color(0xFFFF4245), dark = true)

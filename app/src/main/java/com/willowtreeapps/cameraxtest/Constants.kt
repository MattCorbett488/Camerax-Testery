package com.willowtreeapps.cameraxtest

import android.Manifest
import android.util.Size
import android.graphics.Matrix
import java.util.concurrent.TimeUnit

// This is an arbitrary number we are using to keep tab of the permission
// request. Where an app has multiple context for requesting permission,
// this can help differentiate the different contexts
const val REQUEST_CODE_PERMISSIONS = 10

// This is an array of all the permission specified in the manifest
val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
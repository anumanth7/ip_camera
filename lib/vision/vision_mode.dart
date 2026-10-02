enum VisionMode {
  normal,
  night,
  thermal,
}

extension VisionModeExt on VisionMode {
  String get displayName {
    switch (this) {
      case VisionMode.normal:
        return 'NORMAL VISION';
      case VisionMode.night:
        return 'NIGHT VISION';
      case VisionMode.thermal:
        return 'PSEUDO-THERMAL';
    }
  }

  String? get disclaimer {
    switch (this) {
      case VisionMode.normal:
        return null;
      case VisionMode.night:
        return 'NIGHT VISION - Software Enhanced\n(Gamma adjusted low-light enhancement; not true infrared)';
      case VisionMode.thermal:
        return 'PSEUDO-THERMAL - No Temperature Measurement\n(Ironbow false-color mapping based on relative luminance)';
    }
  }

  String get shortTag {
    switch (this) {
      case VisionMode.normal:
        return 'NORMAL';
      case VisionMode.night:
        return 'NIGHT_ENHANCED';
      case VisionMode.thermal:
        return 'PSEUDO_THERMAL';
    }
  }
}

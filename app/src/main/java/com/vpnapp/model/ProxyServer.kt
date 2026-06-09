package com.vpnapp.model

data class ProxyServer(
    val name: String,
    val server: String,
    val port: Int,
    val type: String,
    var pingMs: Long = -1L,   // -1 = not tested, -2 = dead
) {
    val isAlive get() = pingMs >= 0

    val flagEmoji: String get() {
        val s = "$name $server".lowercase()
        return when {
            s.containsAny("germany","frankfurt","munich","dusseldorf") -> "🇩🇪"
            s.containsAny("netherlands","amsterdam","rotterdam") -> "🇳🇱"
            s.containsAny(" uk","united kingdom","london","britain","england") -> "🇬🇧"
            s.containsAny("france","paris") -> "🇫🇷"
            s.containsAny("sweden","stockholm") -> "🇸🇪"
            s.containsAny("finland","helsinki") -> "🇫🇮"
            s.containsAny("norway","oslo") -> "🇳🇴"
            s.containsAny("austria","vienna") -> "🇦🇹"
            s.containsAny("switzerland","swiss","zurich","geneva") -> "🇨🇭"
            s.containsAny("poland","warsaw") -> "🇵🇱"
            s.containsAny("czech","prague") -> "🇨🇿"
            s.containsAny("russia","moscow","msk") -> "🇷🇺"
            s.containsAny("ukraine","kyiv") -> "🇺🇦"
            s.containsAny("usa","united states","new york","chicago","dallas","los angeles","seattle","ashburn","virginia","ohio","oregon","california") -> "🇺🇸"
            s.containsAny("canada","toronto","montreal","vancouver") -> "🇨🇦"
            s.containsAny("japan","tokyo","osaka") -> "🇯🇵"
            s.containsAny("singapore") -> "🇸🇬"
            s.containsAny("korea","seoul") -> "🇰🇷"
            s.containsAny("hong kong","hongkong") -> "🇭🇰"
            s.containsAny("taiwan","taipei") -> "🇹🇼"
            s.containsAny("australia","sydney","melbourne") -> "🇦🇺"
            s.containsAny("turkey","istanbul","ankara") -> "🇹🇷"
            s.containsAny("latvia","riga") -> "🇱🇻"
            s.containsAny("lithuania","vilnius") -> "🇱🇹"
            s.containsAny("estonia","tallinn") -> "🇪🇪"
            s.containsAny("bulgaria","sofia") -> "🇧🇬"
            s.containsAny("romania","bucharest") -> "🇷🇴"
            s.containsAny("hungary","budapest") -> "🇭🇺"
            s.containsAny("serbia","belgrade") -> "🇷🇸"
            s.containsAny("spain","madrid","barcelona") -> "🇪🇸"
            s.containsAny("italy","rome","milan") -> "🇮🇹"
            s.containsAny("brazil","sao paulo") -> "🇧🇷"
            s.containsAny("india","mumbai","delhi","bangalore") -> "🇮🇳"
            s.containsAny("uae","dubai","abu dhabi") -> "🇦🇪"
            s.containsAny("moldova","chisinau") -> "🇲🇩"
            else -> "🌐"
        }
    }

    val displayType get() = type.uppercase()

    val pingColor get() = when {
        !isAlive      -> PingColor.DEAD
        pingMs < 100  -> PingColor.LOW
        pingMs < 250  -> PingColor.MID
        else          -> PingColor.HIGH
    }
}

enum class PingColor { LOW, MID, HIGH, DEAD }

private fun String.containsAny(vararg tokens: String) =
    tokens.any { this.contains(it) }

package com.example.data.remote

data class CpfValidationResult(
    val digitsOnly: String,
    val formatted: String,
    val masked: String,
    val valid: Boolean,
    val fiscalRegion: String
)

object CpfValidator {

    fun digitsOnly(raw: String): String = raw.filter { it.isDigit() }

    fun formatCpf(raw: String): String {
        val d = digitsOnly(raw).take(11)
        return buildString {
            d.forEachIndexed { index, c ->
                append(c)
                if (index == 2 && d.length > 3) append('.')
                if (index == 5 && d.length > 6) append('.')
                if (index == 8 && d.length > 9) append('-')
            }
        }
    }

    fun maskCpf(raw: String): String {
        val d = digitsOnly(raw)
        if (d.length != 11) return formatCpf(raw)
        return "${d.substring(0, 3)}.***.***-${d.substring(9, 11)}"
    }

    fun validate(raw: String): CpfValidationResult {
        val d = digitsOnly(raw)
        if (d.length != 11) {
            return CpfValidationResult(
                digitsOnly = d,
                formatted = formatCpf(d),
                masked = formatCpf(d),
                valid = false,
                fiscalRegion = "CPF incompleto (11 dígitos necessários)"
            )
        }

        val allSame = d.all { it == d[0] }
        val valid = if (allSame) {
            false
        } else {
            val numbers = d.map { it.digitToInt() }
            val firstSum = (0 until 9).sumOf { i -> numbers[i] * (10 - i) }
            val firstMod = (firstSum * 10) % 11
            val dv1 = if (firstMod == 10) 0 else firstMod

            val secondSum = (0 until 10).sumOf { i -> numbers[i] * (11 - i) }
            val secondMod = (secondSum * 10) % 11
            val dv2 = if (secondMod == 10) 0 else secondMod

            dv1 == numbers[9] && dv2 == numbers[10]
        }

        val region = fiscalRegionFromDigit(d[8])
        return CpfValidationResult(
            digitsOnly = d,
            formatted = formatCpf(d),
            masked = maskCpf(d),
            valid = valid,
            fiscalRegion = region
        )
    }

    fun fiscalRegionFromDigit(digit: Char): String = when (digit) {
        '0' -> "10ª Região Fiscal — Rio Grande do Sul (RS)"
        '1' -> "1ª Região Fiscal — DF, GO, MS, MT e TO"
        '2' -> "2ª Região Fiscal — AC, AM, AP, PA, RO e RR"
        '3' -> "3ª Região Fiscal — CE, MA e PI"
        '4' -> "4ª Região Fiscal — AL, PB, PE e RN"
        '5' -> "5ª Região Fiscal — BA e SE"
        '6' -> "6ª Região Fiscal — Minas Gerais (MG)"
        '7' -> "7ª Região Fiscal — ES e RJ"
        '8' -> "8ª Região Fiscal — São Paulo (SP)"
        '9' -> "9ª Região Fiscal — PR e SC"
        else -> "Região Fiscal desconhecida"
    }

    fun extractDdd(phoneRaw: String): String? {
        val digits = digitsOnly(phoneRaw)
        val normalized = when {
            digits.startsWith("55") && digits.length >= 12 -> digits.drop(2)
            else -> digits
        }
        return if (normalized.length >= 2) normalized.substring(0, 2) else null
    }

    fun formatPhone(raw: String): String {
        val d = digitsOnly(raw).take(11)
        return when {
            d.isEmpty() -> ""
            d.length <= 2 -> "($d"
            d.length <= 6 -> "(${d.substring(0, 2)}) ${d.substring(2)}"
            d.length <= 10 -> "(${d.substring(0, 2)}) ${d.substring(2, 6)}-${d.substring(6)}"
            else -> "(${d.substring(0, 2)}) ${d.substring(2, 7)}-${d.substring(7)}"
        }
    }
}

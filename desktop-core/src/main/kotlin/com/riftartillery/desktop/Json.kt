package com.riftartillery.desktop

internal object Json {
    fun write(value:Any?):String=when(value) {
        null->"null";is Boolean,is Int,is Long->value.toString()
        is Float->if(value.isFinite()) value.toString() else "0"
        is Double->if(value.isFinite()) value.toString() else "0"
        is String->"\""+buildString {for(c in value) append(when(c) {'"'->"\\\"";'\\'->"\\\\";'\n'->"\\n";'\r'->"\\r";'\t'->"\\t";else->if(c<' ') "\\u%04x".format(c.code) else c.toString()})}+"\""
        is Map<*,*>->value.entries.joinToString(",","{","}") {write(it.key.toString())+":"+write(it.value)}
        is Iterable<*>->value.joinToString(",","[","]") {write(it)}
        else->error("Unsupported JSON value: ${value.javaClass}")
    }
    fun read(text:String):Map<String,Any?> {
        require(text.length<=2_000_000)
        var pos=0
        fun whitespace() {while(pos<text.length && text[pos].isWhitespace()) pos++}
        fun string():String {
            require(text[pos++]=='"');val out=StringBuilder()
            while(pos<text.length) {val c=text[pos++];if(c=='"') return out.toString()
                if(c!='\\') out.append(c) else {require(pos<text.length);out.append(when(val e=text[pos++]) {'n'->'\n';'r'->'\r';'t'->'\t';'"'->'"';'\\'->'\\';'u'->{require(pos+4<=text.length);text.substring(pos,pos+4).toInt(16).toChar().also {pos+=4}};else->error("Invalid escape: $e")})}
            };error("Unterminated string")
        }
        fun value():Any? {
            whitespace();require(pos<text.length)
            if(text[pos]=='"') return string()
            val start=pos
            while(pos<text.length && text[pos] !in ",}] \r\n\t") pos++
            require(pos>start)
            return when(val token=text.substring(start,pos)) {"null"->null;"true"->true;"false"->false;else->token.toDouble().also {require(it.isFinite())}}
        }
        whitespace();require(text.getOrNull(pos++)=='{');val result=linkedMapOf<String,Any?>();whitespace()
        if(text.getOrNull(pos)=='}') {pos++;whitespace();require(pos==text.length);return result}
        while(true) {whitespace();val key=string();whitespace();require(text.getOrNull(pos++)==':');result[key]=value();whitespace();when(text.getOrNull(pos++)) {'}'->break;','->Unit;else->error("Invalid object")}}
        whitespace();require(pos==text.length);return result
    }
}

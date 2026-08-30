package hu.konyvtar.tts.reader

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * XML-olvasó a parsereknek.
 *
 * Szándékosan nem az `android.util.Xml`-t használjuk: azzal a parserek csak
 * telefonon futnának, így viszont sima JUnit-tal tesztelhetők. A névtérkezelés
 * kikapcsolva marad — a parserek a teljes, előtaggal együtt kapott neveket
 * várják (`dc:title`), és a régi viselkedés is pontosan ez volt.
 */
internal object XmlReader {

    fun newParser(): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        return factory.newPullParser()
    }
}

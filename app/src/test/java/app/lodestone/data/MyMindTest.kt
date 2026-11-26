package app.lodestone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MyMindTest {
    @Test fun csvHandlesQuotesCommasAndLineBreaks() {
        val rows = MyMind.parseCsv("﻿title,note\r\n\"Hello, world\",\"line one\nline \"\"two\"\"\"\r\nplain,text\r\n")
        assertEquals(3, rows.size)
        assertEquals(listOf("title", "note"), rows[0])
        assertEquals(listOf("Hello, world", "line one\nline \"two\""), rows[1])
        assertEquals(listOf("plain", "text"), rows[2])
    }

    @Test fun columnsAreFoundByHeaderName() {
        val rows = MyMind.parseCsv("id,Type,Title,URL,Content,Tags,Created\n1,note,A,,Body text,\"a, b\",2025-03-04T10:00:00Z\n")
        val cols = MyMind.mapColumns(rows)
        assertTrue(cols.hasHeader)
        assertEquals(2, cols.title)
        assertEquals(3, cols.url)
        assertEquals(listOf(4), cols.notes)
        assertEquals(5, cols.tags)
        assertEquals(6, cols.created)
    }

    @Test fun aRowWithAUrlBecomesALink() {
        val rows = MyMind.parseCsv("title,url,note,tags\nGood read,https://example.com/a,Worth it,\"#read, later\"\n")
        val card = MyMind.cardOf(rows[1], MyMind.mapColumns(rows))
        assertTrue(card is MyMind.Card.Link)
        card as MyMind.Card.Link
        assertEquals("https://example.com/a", card.url)
        assertEquals("Good read", card.title)
        assertEquals("Worth it", card.note)
        assertEquals(listOf("read", "later"), card.tags)
    }

    @Test fun aRowWithOnlyTextBecomesANote() {
        val rows = MyMind.parseCsv("title,note\n,\"Remember the milk\nand the eggs\"\n")
        val card = MyMind.cardOf(rows[1], MyMind.mapColumns(rows)) as MyMind.Card.Note
        assertEquals("Remember the milk", card.title)
        assertTrue(card.text.contains("eggs"))
    }

    @Test fun aRowNamingAFileBecomesMedia() {
        val rows = MyMind.parseCsv("title,file\nSunset,images/sunset.jpg\n")
        val card = MyMind.cardOf(rows[1], MyMind.mapColumns(rows)) as MyMind.Card.Media
        assertEquals("images/sunset.jpg", card.file)
    }

    @Test fun withoutAHeaderTheColumnsAreGuessedFromTheValues() {
        val rows = MyMind.parseCsv("https://a.example/1,first thing\nhttps://b.example/2,second thing that is longer\nhttps://c.example/3,third\n")
        val cols = MyMind.mapColumns(rows)
        assertEquals(false, cols.hasHeader)
        assertEquals(0, cols.url)
        assertEquals(listOf(1), cols.notes)
        val card = MyMind.cardOf(rows[0], cols) as MyMind.Card.Link
        assertEquals("https://a.example/1", card.url)
    }

    @Test fun timesAcceptIsoAndUnixSecondsAndMilliseconds() {
        assertNotNull(MyMind.parseTime("2025-03-04T10:00:00Z"))
        assertNotNull(MyMind.parseTime("2025-03-04 10:00:00"))
        assertNotNull(MyMind.parseTime("2025-03-04"))
        assertEquals(1_700_000_000_000L, MyMind.parseTime("1700000000"))
        assertEquals(1_700_000_000_000L, MyMind.parseTime("1700000000000"))
        assertNull(MyMind.parseTime("yesterday"))
        assertNull(MyMind.parseTime(""))
    }

    @Test fun emptyRowsAreIgnored() {
        val rows = MyMind.parseCsv("title,note\n,\n")
        assertNull(MyMind.cardOf(listOf("", ""), MyMind.mapColumns(rows)))
    }
}

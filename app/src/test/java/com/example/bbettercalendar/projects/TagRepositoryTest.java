package com.example.bbettercalendar.projects;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.concurrent.atomic.AtomicInteger;

public class TagRepositoryTest {

    private TagDAO tagDao;
    private TagRepository repo;
    private int transactions;

    @Before
    public void setUp() {
        tagDao = mock(TagDAO.class);
        transactions = 0;
        TransactionRunner tx = block -> {
            transactions++;
            block.run();
        };
        repo = new TagRepository(tagDao, tx, Runnable::run, new DirectExecutor());
    }

    @Test
    public void deletingATagClearsItFromTasksBeforeRemovingIt() {
        Tag tag = new Tag("Lectura", "book", "primary", Tag.KIND_TAG, 1);
        tag.id = 5;

        repo.delete(tag);

        InOrder order = inOrder(tagDao);
        order.verify(tagDao).clearTagFromEntries(5);
        order.verify(tagDao).delete(tag);
        verify(tagDao, never()).clearAreaFromProjects(anyInt());
        assertEquals("one transaction", 1, transactions);
    }

    @Test
    public void deletingAnAreaClearsItFromProjectsNotFromTasks() {
        Tag area = new Tag("Casa", "home", "", Tag.KIND_AREA, 2);
        area.id = 8;

        repo.delete(area);

        InOrder order = inOrder(tagDao);
        order.verify(tagDao).clearAreaFromProjects(8);
        order.verify(tagDao).delete(area);
        verify(tagDao, never()).clearTagFromEntries(anyInt());
        assertEquals("one transaction", 1, transactions);
    }

    @Test
    public void createPutsTheNewTagAtTheEndOfItsKindAndReturnsItsId() {
        when(tagDao.nextPosition(Tag.KIND_AREA)).thenReturn(4);
        when(tagDao.insert(any(Tag.class))).thenReturn(42L);
        AtomicInteger received = new AtomicInteger();

        repo.create(Tag.KIND_AREA, "Viajes", "plane", "", received::set);

        org.mockito.ArgumentCaptor<Tag> captor = org.mockito.ArgumentCaptor.forClass(Tag.class);
        verify(tagDao).insert(captor.capture());
        assertEquals("Viajes", captor.getValue().name);
        assertEquals("plane", captor.getValue().iconKey);
        assertEquals(Tag.KIND_AREA, captor.getValue().kind);
        assertEquals(4, captor.getValue().position);
        assertEquals(42, received.get());
    }

    @Test
    public void updateSavesTheEditedTag() {
        Tag tag = new Tag("Llamada", "phone", "muted", Tag.KIND_TAG, 3);
        tag.id = 6;

        repo.update(tag);

        verify(tagDao).update(tag);
    }
}

/**
 * OpenKM, Open Document Management System (http://www.openkm.com)
 * Copyright (c) Paco Avila & Josep Llort
 * <p>
 * No bytes were intentionally harmed during the development of this application.
 * <p>
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.openkm.extension.frontend.client.widget.messaging.stack.messagereceived;

import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.TableCellElement;
import com.google.gwt.dom.client.TableRowElement;
import com.google.gwt.dom.client.TableSectionElement;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.Event;
import com.openkm.extension.frontend.client.widget.messaging.MessagingToolBarBox;

/**
 * Extends FlexTable functionalities
 *
 * @author jllort
 *
 */
public class ExtendedFlexTable extends FlexTable {

	private int mouseX = 0;
	private int mouseY = 0;
	private boolean panelSelected = true; // Indicates if panel is selected
	private int selectedRow = -1;

	/**
	 * ExtendedFlexTable
	 */
	public ExtendedFlexTable() {
		super();

		// Adds double click event control to table ( on default only has CLICK )
		sinkEvents(Event.ONDBLCLICK | Event.MOUSEEVENTS);
		addClickHandler(new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				// Mark selected row or orders rows if header row (0) is clicked
				// And row must be other than the selected one
				markSelectedRow(getCellForEvent(event).getRowIndex());
				MessagingToolBarBox.get().messageDashboard.messageStack.messageReceived.refreshMessagesReceived();
			}
		});
	}

	/* (non-Javadoc)
	 * @see com.google.gwt.user.client.EventListener#onBrowserEvent(com.google.gwt.user.client.Event)
	 */
	public void onBrowserEvent(com.google.gwt.user.client.Event event) {
		int selectedRow = 0;

		String eventType = event.getType().toLowerCase();

		if ("dblclick".equals(eventType) || "mousedown".equals(eventType)) {
			TableCellElement td = getMouseEventTargetCell(event);
			if (td == null) return;
			TableRowElement tr = td.getParentElement().cast();
			TableSectionElement body = tr.getParentElement().cast();
			selectedRow = -1;
			for (int i = 0; i < body.getChildCount(); i++) {
				if (body.getChild(i).equals(tr)) {
					selectedRow = i;
					break;
				}
			}
		}

		if (selectedRow >= 0) {
			mouseX = event.getClientX();
			mouseY = event.getClientY();

			if ("dblclick".equals(eventType)) {
				event.stopPropagation();
				MessagingToolBarBox.get().messageDashboard.messageStack.messageReceived.refreshMessagesReceived();
			} else if ("mousedown".equals(eventType)) {
				switch (event.getButton()) {
					case com.google.gwt.user.client.Event.BUTTON_RIGHT:
						markSelectedRow(selectedRow);
						MessagingToolBarBox.get().messageDashboard.messageStack.messageReceived.menuPopup.setPopupPosition(mouseX, mouseY);
						MessagingToolBarBox.get().messageDashboard.messageStack.messageReceived.menuPopup.show();
						event.preventDefault();
						break;
					default:
						break;
				}
			}
		}
		super.onBrowserEvent(event);
	}

	/**
	 * markSelectedRow
	 *
	 * @param row
	 */
	private void markSelectedRow(int row) {
		setSelectedPanel(true);
		if (row != selectedRow) {
			styleRow(selectedRow, false);
			styleRow(row, true);
			selectedRow = row;
		}
	}

	/**
	 * Adds a new row
	 *
	 * @param user
	 */
	public void addRow(String user, boolean seen) {
		int rows = getRowCount();
		if (!seen) {
			setHTML(rows, 0, "<b>" + user + "</b>");
		} else {
			setHTML(rows, 0, user);
		}
		setHTML(rows, 1, user);
		setHTML(rows, 2, "");

		// The hidden column extends table to 100% width
		CellFormatter cellFormatter = getCellFormatter();
		cellFormatter.setWidth(rows, 2, "100%");
		cellFormatter.setVisible(rows, 1, false);

		getRowFormatter().setStyleName(rows, "okm-SearchSaved");
		setRowWordWarp(rows, 3, false);
	}

	/**
	 * markActualRowAsSeen
	 */
	public void markActualRowAsSeen() {
		if (selectedRow >= 0) {
			setHTML(selectedRow, 0, getHTML(selectedRow, 1));
		}
	}

	/**
	 * Set the WordWarp for all the row cells
	 *
	 * @param row The row cell
	 * @param columns Number of row columns
	 * @param warp
	 */
	private void setRowWordWarp(int row, int columns, boolean warp) {
		CellFormatter cellFormatter = getCellFormatter();
		for (int i = 0; i < columns; i++) {
			cellFormatter.setWordWrap(row, i, false);
		}
	}

	/**
	 * Method originally copied from HTMLTable superclass where it was defined private
	 * Now implemented differently to only return target cell if it's part of 'this' table
	 */
	private TableCellElement getMouseEventTargetCell(NativeEvent event) {
		com.google.gwt.dom.client.Element td = com.google.gwt.dom.client.Element.as(event.getEventTarget());
		while (td != null && !"td".equalsIgnoreCase(td.getTagName())) {
			if (td == null || td == getElement()) return null;
			td = td.getParentElement();
		}
		if (td == null) return null;
		TableRowElement tr = td.getParentElement().cast();
		TableSectionElement body = tr.getParentElement().cast();
		if (body == getBodyElement().cast()) {
			return td.cast();
		}
		return null;
	}

	/**
	 * Sets the selected panel value
	 *
	 * @param selected The selected panel value
	 */
	public void setSelectedPanel(boolean selected) {
		// Case panel is not still selected and must enable this and disable result search panel
		if (!isPanelSelected() && selected) {
			MessagingToolBarBox.get().messageDashboard.messageBrowser.messageDetail.addStyleName("okm-PanelSelected");
		} else {
			MessagingToolBarBox.get().messageDashboard.messageBrowser.messageDetail.removeStyleName("okm-PanelSelected");
		}

		if (selected) {
			MessagingToolBarBox.get().messageDashboard.messageStack.scrollMessageReceivedPanel.addStyleName("okm-PanelSelected");
		} else {
			MessagingToolBarBox.get().messageDashboard.messageStack.scrollMessageReceivedPanel.removeStyleName("okm-PanelSelected");
		}
		panelSelected = selected;
	}

	/**
	 * Change the style row selected or unselected
	 *
	 * @param row The row afected
	 * @param selected Indicates selected unselected row
	 */
	public void styleRow(int row, boolean selected) {
		// Ensures that header is never changed
		if (row >= 0) {
			if (selected) {
				getRowFormatter().addStyleName(row, "okm-Table-SelectedRow");
			} else {
				getRowFormatter().removeStyleName(row, "okm-Table-SelectedRow");
			}
		}
	}

	/**
	 * Removes all rows except the first
	 */
	public void removeAllRows() {
		// Resets selected Rows and Col values
		selectedRow = -1;
		super.removeAllRows();
	}

	/**
	 * Finds row by id
	 *
	 * @param id The id
	 * @return The selected row
	 */
	public int findSelectedRowById(String id) {
		int selected = -1;
		int rowIndex = 0;
		boolean found = false;

		// Looking for id on directories
		while (!found && rowIndex < getRowCount()) {
			if (getHTML(rowIndex, 1).equals(id)) {
				selected = rowIndex;
				found = true;
			}
			rowIndex++;
		}
		return selected;
	}

	/**
	 * Gets the selected row value
	 *
	 * @return The selected row value
	 */
	public int getSelectedRow() {
		return selectedRow;
	}

	/**
	 * getSelectedId
	 *
	 * @return
	 */
	public String getSelectedId() {
		if (selectedRow >= 0 && getRowCount() > 0) {
			return getHTML(selectedRow, 1);
		} else {
			return "";
		}
	}

	/**
	 * After deletes rows selects a new row
	 */
	public void selectPrevRow() {
		// After deletes document or folder selects a previos row if not 0 or the next if exists ( next row is actual after delete )
		// RowCount minor value is 1 for header titles
		if (getRowCount() > 1) {
			if (selectedRow > 0) {
				selectedRow--;
			}
			styleRow(selectedRow, true);

		} else {
			// Case deletes all table rows
			selectedRow = -1;
		}
	}

	/**
	 * setSelectedRow
	 *
	 * @param row
	 */
	public void setSelectedRow(int row) {
		if (row >= 0 && row < getRowCount()) {
			markSelectedRow(row);
		}
	}

	/**
	 * Indicates if panel is selected
	 *
	 * @return The value of panel ( selected )
	 */
	public boolean isPanelSelected() {
		return panelSelected;
	}

	/**
	 * Gets the X position on mouse click
	 *
	 * @return The x position on mouse click
	 */
	public int getMouseX() {
		return mouseX;
	}

	/**
	 * Gets the Y position on mouse click
	 *
	 * @return The y position on mouse click
	 */
	public int getMouseY() {
		return mouseY;
	}
}

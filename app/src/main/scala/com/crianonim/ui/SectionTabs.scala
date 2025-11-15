package com.crianonim.ui

import tyrian.Html.*
import tyrian.*

object SectionTabs {

  /** Represents a single tab item with an identifier and label
    *
    * @param id
    *   Unique identifier for the tab
    * @param label
    *   Display label for the tab
    */
  case class TabItem(id: String, label: String)

  /** Props for configuring the SectionTabs component
    *
    * @param tabs
    *   List of tab items to display
    * @param activeTabId
    *   The ID of the currently active tab
    * @param onTabClick
    *   Function to generate a message when a tab is clicked, receives the tab ID
    */
  case class Props[A](
      tabs: List[TabItem],
      activeTabId: String,
      onTabClick: String => A
  )

  private def baseTabClasses: String =
    "px-6 py-3 font-medium text-sm transition-all duration-200 cursor-pointer focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"

  private def activeTabClasses: String =
    "text-blue-600 border-b-2 border-blue-600 bg-blue-50"

  private def inactiveTabClasses: String =
    "text-gray-600 border-b-2 border-transparent hover:text-gray-900 hover:bg-gray-100"

  /** Renders a horizontal tab navigation component
    *
    * @param props
    *   Configuration for the tabs component
    * @return
    *   HTML element with tab navigation
    */
  def apply[A](props: Props[A]): Html[A] = {
    div(cls := "w-full")(
      div(
        cls := "flex border-b border-gray-200 overflow-x-auto",
        attribute("role", "tablist"),
        attribute("aria-label", "Section tabs")
      )(
        props.tabs.map { tab =>
          val isActive = tab.id == props.activeTabId
          val tabClasses = if (isActive) {
            s"$baseTabClasses $activeTabClasses"
          } else {
            s"$baseTabClasses $inactiveTabClasses"
          }

          button(
            cls := tabClasses,
            onClick(props.onTabClick(tab.id)),
            attribute("role", "tab"),
            attribute("aria-selected", if (isActive) "true" else "false"),
            attribute("aria-controls", s"${tab.id}-panel"),
            attribute("id", s"${tab.id}-tab"),
            attribute("type", "button")
          )(
            div()(text(tab.label))
          )
        }*
      )
    )
  }

  /** Renders a tab panel container with proper ARIA attributes
    *
    * @param tabId
    *   The ID of the tab this panel belongs to
    * @param isActive
    *   Whether this panel should be visible
    * @param content
    *   The content to display in the panel
    * @return
    *   HTML element for the tab panel
    */
  def panel[A](tabId: String, isActive: Boolean)(content: Html[A]*): Html[A] = {
    div(
      cls := s"py-4 ${if (!isActive) "hidden" else ""}",
      attribute("role", "tabpanel"),
      attribute("aria-labelledby", s"$tabId-tab"),
      attribute("id", s"$tabId-panel"),
      attribute("tabindex", "0")
    )(
      content*
    )
  }

  /** Convenience method for creating a list of tabs with simple string labels
    *
    * @param labels
    *   List of tab labels (IDs will be auto-generated from labels)
    * @param activeTabId
    *   The ID of the currently active tab
    * @param onTabClick
    *   Function to generate a message when a tab is clicked
    * @return
    *   HTML element with tab navigation
    */
  def fromLabels[A](labels: List[String], activeTabId: String, onTabClick: String => A): Html[A] = {
    val tabs = labels.map { label =>
      TabItem(
        id = label.toLowerCase.replaceAll("\\s+", "-"),
        label = label
      )
    }
    apply(Props(tabs, activeTabId, onTabClick))
  }
}

package com.example.flipviewtest

import android.R
import android.content.ClipData.Item
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.example.flipviewtest.databinding.ListItemBinding


class NormalAdapter(context: Context, items: ArrayList<Item>) :
    BaseAdapter() {
    private val context //context
            : Context
    private val items //data source of the list adapter
            : ArrayList<Item>

    //public constructor
    init {
        this.context = context
        this.items = items
    }

    override fun getCount(): Int {
        return items.size //returns total of items in the list
    }

    override fun getItem(position: Int): Any {
        return items[position] //returns list item at the specified position
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View? {
        // inflate the layout for each list row
        var convertView: View? = convertView
        if (convertView == null) {
            val binding = ListItemBinding.inflate(LayoutInflater.from(context), parent, false)
            convertView = binding.root
            binding.heading.text = "12354556454"

        }

        // get current item to be displayed
        val currentItem = getItem(position) as Item

        // get the TextView for item name and item description



        // returns the view for the current row
        return convertView
    }
}
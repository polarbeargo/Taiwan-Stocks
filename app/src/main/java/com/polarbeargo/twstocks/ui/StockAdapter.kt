package com.polarbeargo.twstocks.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.polarbeargo.twstocks.R
import com.polarbeargo.twstocks.databinding.ItemStockBinding
import com.polarbeargo.twstocks.model.PriceTone
import com.polarbeargo.twstocks.model.Stock

class StockAdapter(private val onClick: (Stock) -> Unit) :
    ListAdapter<Stock, StockAdapter.Holder>(Diff) {
    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemStockBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemStockBinding) : RecyclerView.ViewHolder(binding.root) {
        private val formatter = StockFormatter(binding.root.context.getString(R.string.unavailable))

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onClick(getItem(position))
            }
        }

        fun bind(stock: Stock) = with(binding) {
            code.text = stock.code
            name.text = stock.name
            date.text = root.context.getString(R.string.trade_date, formatter.date(stock.date))
            opening.text = formatter.price(stock.opening)
            highest.text = formatter.price(stock.highest)
            lowest.text = formatter.price(stock.lowest)
            closing.text = formatter.price(stock.closing)
            monthlyAverage.text = formatter.price(stock.monthlyAverage)
            change.text = formatter.change(stock.change)
            transactions.text = formatter.count(stock.transactions)
            volume.text = formatter.count(stock.volume)
            value.text = formatter.count(stock.value)
            closing.setTextColor(color(stock.closingTone))
            change.setTextColor(color(stock.changeTone))
            root.contentDescription = root.context.getString(R.string.card_description, stock.code, stock.name)
        }

        private fun color(tone: PriceTone): Int = ContextCompat.getColor(
            binding.root.context,
            when (tone) {
                PriceTone.UP -> R.color.price_up
                PriceTone.DOWN -> R.color.price_down
                PriceTone.NEUTRAL -> R.color.text_primary
            },
        )
    }

    private object Diff : DiffUtil.ItemCallback<Stock>() {
        override fun areItemsTheSame(oldItem: Stock, newItem: Stock): Boolean = oldItem.code == newItem.code
        override fun areContentsTheSame(oldItem: Stock, newItem: Stock): Boolean = oldItem == newItem
    }
}

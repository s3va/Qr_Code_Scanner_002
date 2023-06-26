package tk.kvakva.qrcodescanner002

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import tk.kvakva.qrcodescanner002.databinding.CodeItemBinding

//import tk.kvakva.qrcodescanner002.databinding.CodeItemBinding

data class DecodedText(val txt: String, var selected: Boolean)

class ScannedCodesRecViewAdapter(val sendDecodedText: (String) -> Unit) :
    RecyclerView.Adapter<ScannedCodesRecViewAdapter.CodeViewHolder>() {

    var data = listOf<DecodedText>()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getItemCount() = data.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CodeViewHolder {
        return CodeViewHolder.from(parent, sendDecodedText)
    }

    override fun onBindViewHolder(holder: CodeViewHolder, position: Int) {
        holder.bind(data[position], sendDecodedText)
    }

    class CodeViewHolder private constructor(val binding: CodeItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DecodedText, sendDecodedText: (String) -> Unit) {
            binding.text = item

            if (item.selected) {
                binding.root.setBackgroundResource(android.R.drawable.editbox_background)
            } else {
                binding.root.background = null
            }
            fun f(){
                if (!item.selected) {
                    item.selected = true
                    binding.root.setBackgroundResource(android.R.drawable.editbox_background)
                } else {
                    item.selected = false
                    binding.root.background = null
                }
            }
            binding.textView.setOnClickListener {
                f()
            }

            /// https://stackoverflow.com/questions/22653641/using-onclick-on-textview-with-selectable-text-how-to-avoid-double-click
            binding.textView.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    f()
                }
            }
            binding.button.setOnClickListener {
                //if (vh.adapterPosition != RecyclerView.NO_POSITION){
                sendDecodedText(item.txt)
                //}
            }
            binding.executePendingBindings()
        }

        companion object {
            fun from(parent: ViewGroup, sendDecodedText: (String) -> Unit): CodeViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                val binding = CodeItemBinding.inflate(layoutInflater, parent, false)
                val vh = CodeViewHolder(binding)
                /*                vh.binding.button.setOnClickListener {
                                    if (vh.adapterPosition != RecyclerView.NO_POSITION){
                                        sendDecodedText(vh.binding.textView.text.toString())
                                    }
                                }*/
                return vh
            }
        }
    }
}

private const val TAG = "ScannedCodesRecViewAdap"
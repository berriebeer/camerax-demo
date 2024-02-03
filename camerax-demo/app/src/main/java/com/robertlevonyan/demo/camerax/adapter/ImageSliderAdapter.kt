import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.robertlevonyan.demo.camerax.R
import com.robertlevonyan.demo.camerax.databinding.ItemTouchImageViewBinding

class ImageSliderAdapter(
    private val images: ArrayList<Uri>,
    private val onZoomChanged: (Boolean) -> Unit
) : RecyclerView.Adapter<ImageSliderAdapter.ViewHolder>() {
    class ViewHolder(val binding: ItemTouchImageViewBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTouchImageViewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val uri = images[position]
        holder.binding.touchImageView.load(uri) {
            crossfade(true)
            placeholder(R.drawable.solid_black) // replace with your placeholder drawable resource
            error(R.drawable.ic_error) // replace with your error drawable resource
        }
        holder.binding.touchImageView.setOnTouchImageViewListener {
            val isZoomed = holder.binding.touchImageView.isZoomed
            onZoomChanged(isZoomed)
        }
    }
        override fun getItemCount() = images.size
    }

